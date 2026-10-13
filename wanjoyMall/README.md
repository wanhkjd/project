# wanjoyMall 后端微服务

Java 21 / Spring Boot **3.5.16** 多模块 Maven 项目。只迁移独立后端微服务及公共组件，不包含旧单体应用和前端。

## 模块

| 模块 | 作用 | 默认端口 | 默认数据库 |
| --- | --- | --- | --- |
| wm-common | 公共模型、异常、自动配置、工具 | — | — |
| wm-api | OpenFeign 客户端与共享 DTO | — | — |
| wm-gateway | WebFlux 网关、JWT、动态路由 | 8080 | — |
| item-service | 商品、搜索、秒杀 | 8081 | hm-item |
| cart-service | 购物车 | 8082 | hm-cart |
| user-service | 用户、地址、余额 | 8084 | hm-user |
| trade-service | 订单、超时关单、秒杀订单 | 8085 | hm-trade |
| pay-service | 支付单、本地消息、对账任务 | 8086 | hm-pay |

Maven 根坐标：`com.wsy:wanjoyMall:1.0.0-SNAPSHOT`；Java 根包：`com.wsy.wanjoymall`；自定义配置前缀：`wm`。

## 构建与测试

需要 JDK 21、Maven 3.9+。在本项目根目录执行：

~~~powershell
java -version
mvn -version
mvn -B -ntp clean verify
~~~

日常构建包含公共配置测试、五个业务应用的隔离启动与接口文档测试、网关启动/JWT/动态路由测试以及原有订单单元测试。
使用 H2、MockMvc 和 Mock 组件验证装配；不会执行真实中间件读写测试。H2 不是实际业务数据库的替代，不代表 MySQL SQL、分布式事务或业务链路已通过验证。

六个可运行服务分别生成 `<模块>/target/<模块>.jar`；两个公共模块生成普通依赖 JAR。当前使用系统 Maven，不依赖 Maven Wrapper 脚本。

## 准备运行环境

按当前要求，业务数据库继续使用已有的 `hm-item`、`hm-cart`、`hm-user`、`hm-trade`、`hm-pay`，项目名、模块名、包名和 `wm` 配置前缀不变。运行新服务会读写这些已有数据库，应先备份，并避免新旧服务的定时任务同时处理同一批数据。此项变更不包含执行 SQL 或修改数据库。RabbitMQ vhost、Nacos 命名空间仍按新项目配置，Nacos 分组保持 DEFAULT_GROUP。

1. **MySQL**：核对表格中的五个已有 `hm-*` 数据库、基础表及账户权限；不要为了项目改名而新建 `wm-*` 库。只有确认缺少对应结构并完成备份后，才人工按需应用增量 SQL。应用账户应使用最小权限。
2. **Redis**：使用专用实例；运行默认 DB1，集成测试固定 DB15。仅换数据库编号不等于实例级隔离，生产库绝不能用于测试。
3. **RabbitMQ**：创建 `/wanjoymall` vhost 及独立用户，启用与服务端版本匹配的 **rabbitmq_delayed_message_exchange** 插件。订单超时消息依赖该插件。
4. **Nacos**：建议使用独立、与客户端 3.0.3 兼容的服务端（例如 3.x），默认 namespace ID 为 `wanjoyMall`，分组为 `DEFAULT_GROUP`，账户按服务端配置。除 HTTP 8848 外须开放服务端实际 gRPC 端口（常见为 9848）。
5. **Apache Seata**：使用独立的 2.5.x 兼容环境，业务库准备官方对应版本 `undo_log` 表。默认启用，事务分组 `wm-trade-group`，通过 Nacos `192.168.100.129:8848` 查找 `public / DEFAULT_GROUP / seata-server`，TC 集群为 `default`。8848 是 Nacos HTTP 端口，不是 TC 事务通信端口；实际 TC 地址由注册实例返回。`seata.config.type=file` 表示事务配置仍由应用提供。若 TC 注册在其他命名空间，单独调整 `seata.registry.nacos.namespace`，不要直接套用业务命名空间。
6. **Elasticsearch**：现有客户端保留 8.11.0，准备兼容的 ES 8.11 服务端、IK 分词插件及商品索引。索引名仍为业务通用名 `items`，必须使用专用集群。支持通过环境变量设置基本认证；HTTPS 证书须由 JVM 信任，不要关闭证书验证。
7. **Sentinel**：可配置控制台；限流/熔断规则需按新服务实际环境设置，本次不包含原控制台规则导出。

### 数据库脚本边界

`sql/wanjoymall-upgrade.sql` **只是增量脚本，不是完整初始化库**。基础表与数据需另外提供；其中部分 `ALTER TABLE` 不幂等，执行前必须核对已有结构和唯一约束冲突。此次迁移没有执行任何 SQL、导入数据、创建中间件账户或修改远程配置。

## 配置方式

`.env.example` 是变量清单。**Spring Boot 不会自动读取 .env 文件**，请在终端、IDE 或容器中设置环境变量。
数据库等空密码不是推荐配置；Seata 模板按用户提供的开发环境使用 Nacos `nacos/nacos`，生产环境必须覆盖凭据。不要把真实密码提交到 Git。

- **local**：通过 `spring.config.import` 加载公共模块内的 `wm-shared-*.yaml`；网关使用本地静态路由。只是不依赖 Nacos 配置中心，**仍然需要 Nacos 服务发现**及业务中间件。
- **dev（默认）**：在 Nacos 的 `wanjoyMall` 命名空间（填写实际 ID）、`DEFAULT_GROUP` 分组，按原文件名分别导入 `deploy/nacos` 中的五个 YAML 模板。网关另需 JSON 类型的 `wm-gateway-routes.json`；购物车另需 `cart-service.yaml`（设置 `wm.cart.max-items`，例如 10），同样放在该命名空间的 `DEFAULT_GROUP` 分组。必需配置缺失会启动失败，不会静默沿用旧项目配置。
- 模板根据源码重建，**没有从原 Nacos 导出**。导入前核对连接地址、凭据、命名空间及环境变量是否齐全。namespace 必须使用 ID 而非显示名称；当前默认 ID 为 `wanjoyMall`，如果实际 ID 不同，请通过 `NACOS_NAMESPACE` 覆盖。配置中心、服务发现和网关动态路由使用同一命名空间与分组，`NACOS_GROUP` 可统一覆盖分组（包括购物车专用配置导入）。
- 各服务默认连接对应的 `hm-*` 业务库；不要在所有进程共享的终端环境中把 `WM_DB_NAME` 设置成一个库。若 IDE/环境变量或 Nacos 中覆盖了 `wm.db.database`、`WM_DB_NAME`、`spring.datasource.url`，需要同步核对。共享 JDBC URL 继续引用 `${wm.db.database}`，不能把共享配置统一写成 `hm-cart`。Mapper 自定义 SQL 不固定数据库名，跟随当前数据源；集成测试继续使用专用测试库，不能指向这些已有业务库。
- 动态路由只在 dev 默认启用。空白或无效更新被拒绝；明确填写 `[]` 才表示清空动态路由。

IDEA 直接运行现在默认启用 `dev`，连接 `192.168.100.129:8848`；若运行配置、环境变量或命令行中显式指定了 `local` 或旧地址，需要移除这些覆盖项。

Seata 通过该 Nacos 查找 TC，不应将 `192.168.100.129:8848` 填入 `seata.service.grouplist.default`。Nacos 配置存在不代表 TC 已启动：请在 `public / DEFAULT_GROUP` 的服务列表中确认 `seata-server` 有健康实例，集群为 `default`。只有 TC 实际注册在其他位置时，才同步调整客户端注册信息。

PowerShell 示例（请填入**新环境**的参数，在每个启动终端分别配置）：

~~~powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:NACOS_ADDR = "192.168.100.129:8848"
$env:NACOS_NAMESPACE = "wanjoyMall" # 命名空间 ID，不是显示名称
$env:NACOS_GROUP = "DEFAULT_GROUP"
$env:WM_DB_HOST = "127.0.0.1"
$env:WM_DB_USER = "wanjoymall"
$env:WM_DB_PASSWORD = "<新环境数据库密码>"
$env:RABBITMQ_USER = "wanjoymall"
$env:RABBITMQ_PASSWORD = "<新环境 RabbitMQ 密码>"
$env:RABBITMQ_VHOST = "/wanjoymall"
# 按需设置 Nacos、Redis、ES 的凭据，完整清单见 .env.example
java -jar .\item-service\target\item-service.jar
~~~

依次启动五个业务服务，再启动网关。每个服务使用独立终端或 IDE 启动配置；Spring Boot 应用主类位于对应模块根包。

如临时设置 `SEATA_ENABLED=false`，跨服务事务保障会被禁用，**不能据此判定交易功能已验收**。
设置 `ELASTICSEARCH_ENABLED=false` 可切换回已有的数据库搜索逻辑，但不等价于 ES 联调通过。

## 接口文档与密钥

- 各业务服务直连端口提供 `/v3/api-docs`、`/swagger-ui/index.html` 和 `/doc.html`。默认网关路由未聚合这些文档地址。
- 使用 Springdoc 2.8.17 生成 OpenAPI 3；保留 Knife4j 4.5.0 静态 UI，不启用与新版 Springdoc 二进制不兼容的旧增强器。原接口和模型说明随注解迁移保留。
- 生产环境应关闭/限制接口文档和静态文档页面；禁用 Springdoc 端点不等于移除所有静态 HTML 资源。
- 用户服务和网关内置相同的**开发用** `wanjoymall.jks`，实际格式为 PKCS12，别名 `wanjoymall`，开发密码 `wanjoymall-dev-key`。
- 迁移保留了原签名密钥材料，只更换文件名、别名与容器密码；这**不是密钥轮换或安全加固**。生产必须通过 `WM_JWT_LOCATION`、`WM_JWT_ALIAS`、`WM_JWT_PASSWORD` 指定外部独立密钥，并让用户服务与网关配对。轮换可能使旧令牌失效。

## 真实中间件集成测试（本次未执行）

现有 Redis/ES 测试保留并标记为 `integration`。仅在专用、可写且允许丢弃的环境中运行：

~~~powershell
# 先设置 WM_TEST_REDIS_*、WM_TEST_ES_*、WM_TEST_DB_HOST 及测试库账户凭据
mvn -B -ntp verify -Pintegration-tests "-Dwm.integration.allow-writes=true"
~~~

此命令会真实写 Redis DB15、修改/删除 `wm-items-integration-test` 索引，并读取 `wm-item-integration-test` 数据库。测试用数据库仍需基础表和样例数据；ES 测试需要 IK 分词插件。不要对生产地址运行。仅启用 profile 而未允许写入时，外部测试会跳过。

## 迁移记录

详见 `docs/migration-report.md`。原有中文 JavaDoc、业务注释及作者署名保留；只更新了命名、配置和 API 改造相关说明。
