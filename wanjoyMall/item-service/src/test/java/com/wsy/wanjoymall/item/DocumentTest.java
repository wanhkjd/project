package com.wsy.wanjoymall.item;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import org.junit.jupiter.api.Tag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wsy.wanjoymall.common.utils.BeanUtils;
import com.wsy.wanjoymall.common.utils.CollUtils;
import com.wsy.wanjoymall.item.domain.po.Item;
import com.wsy.wanjoymall.item.domain.po.ItemDoc;
import com.wsy.wanjoymall.item.service.IItemService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootTest
@ActiveProfiles("integration")
@TestPropertySource(properties = {
        "wm.elasticsearch.enabled=true",
        "wm.elasticsearch.host=${WM_TEST_ES_HOST:127.0.0.1}",
        "wm.elasticsearch.port=${WM_TEST_ES_PORT:9200}",
        "wm.elasticsearch.scheme=http"
})
@Tag("integration")
@EnabledIfSystemProperty(named = "wm.integration.allow-writes", matches = "true")
class DocumentTest {

    private static final String INDEX_NAME = "wm-items-integration-test";

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private IItemService itemService;

    @Test
    void testAddDocument() throws IOException {
        // 1.根据 id 查询商品数据 Item
        Item item = itemService.getById(100002644680L);
        // 2.将 Item 封装为 ItemDoc，Long 类型的 id 会自动转换为 String
        ItemDoc itemDoc = BeanUtils.copyBean(item, ItemDoc.class);
        // 3.创建 IndexRequest 并发送：指定索引库名和文档 id，document 直接传入对象由客户端序列化
        IndexResponse response = client.index(request -> request
                .index(INDEX_NAME)
                .id(itemDoc.getId())
                .document(itemDoc));
        // 4.打印结果
        log.info("新增文档结果：{}", response.result());
    }

    @Test
    void testGetDocumentById() throws IOException {
        // 1.创建 GetRequest 并发送，第二个参数指定 _source 反序列化的目标类型
        GetResponse<ItemDoc> response = client.get(request -> request
                .index(INDEX_NAME)
                .id("100002644680"), ItemDoc.class);
        // 2.文档不存在直接返回
        if (!response.found()) {
            log.info("文档不存在");
            return;
        }
        // 3.获取 _source，客户端已自动反序列化为 ItemDoc
        ItemDoc itemDoc = response.source();
        log.info("itemDoc = {}", itemDoc);
    }

    @Test
    void testDeleteDocument() throws IOException {
        // 1.创建 DeleteRequest 并发送：指定索引库名和文档 id
        DeleteResponse response = client.delete(request -> request
                .index(INDEX_NAME)
                .id("100002644680"));
        // 2.打印结果
        log.info("删除文档结果：{}", response.result());
    }

    @Test
    void testUpdateDocument() throws IOException {
        // 1.准备要局部修改的字段，key 为字段名，value 为新值
        Map<String, Object> doc = new HashMap<>();
        doc.put("price", 58800);
        doc.put("commentCount", 1);
        // 2.创建 UpdateRequest 并发送：doc() 传入部分字段实现局部更新
        UpdateResponse<ItemDoc> response = client.update(request -> request
                .index(INDEX_NAME)
                .id("100002644680")
                .doc(doc), ItemDoc.class);
        // 3.打印结果
        log.info("更新文档结果：{}", response.result());
    }

    @Test
    void testBulk() throws IOException {
        // 1.查询两条商品作为演示数据
        List<Item> items = itemService.lambdaQuery().last("limit 2").list();
        // 2.创建 BulkRequest.Builder，把每个文档封装成一个 index 操作追加进去
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (Item item : items) {
            ItemDoc itemDoc = BeanUtils.copyBean(item, ItemDoc.class);
            br.operations(op -> op.index(idx -> idx
                    .index(INDEX_NAME)
                    .id(itemDoc.getId())
                    .document(itemDoc)));
        }
        // 3.发送批量请求
        BulkResponse response = client.bulk(br.build());
        // 4.errors() 为 true 表示批量操作中存在失败项
        log.info("批量操作是否存在失败：{}", response.errors());
    }

    @Test
    void testLoadItemDocs() throws IOException {
        // 分页查询商品数据，每页 1000 条循环批量导入
        int pageNo = 1;
        int size = 1000;
        while (true) {
            // 1.分页查询 status=1（上架）的商品
            Page<Item> page = itemService.lambdaQuery()
                    .eq(Item::getStatus, 1)
                    .page(new Page<>(pageNo, size));
            List<Item> items = page.getRecords();
            // 2.当前页没有数据，导入结束
            if (CollUtils.isEmpty(items)) {
                break;
            }
            log.info("加载第{}页数据，共{}条", pageNo, items.size());
            // 3.创建 BulkRequest，把本页每个 Item 转成 ItemDoc 后封装为 index 操作
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (Item item : items) {
                // 3.1.Item 转 ItemDoc，id 由 Long 自动转 String
                ItemDoc itemDoc = BeanUtils.copyBean(item, ItemDoc.class);
                // 3.2.向 bulk 中追加一个新增文档的 index 操作
                br.operations(op -> op.index(idx -> idx
                        .index(INDEX_NAME)
                        .id(itemDoc.getId())
                        .document(itemDoc)));
            }
            // 4.发送批量请求
            BulkResponse response = client.bulk(br.build());
            // 5.存在失败项时打印，方便排查
            if (response.errors()) {
                log.error("第{}页存在导入失败的文档", pageNo);
            }
            // 6.翻页
            pageNo++;
        }
    }
}
