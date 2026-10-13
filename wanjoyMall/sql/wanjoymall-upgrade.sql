-- 注意：本脚本面向已有 hm-* 业务库；包含非幂等 ALTER TABLE，备份并核对表结构后按需执行，不会由应用自动执行。
-- hm-user：余额扣款流水，pay_order_id 唯一 => 同一支付单只扣一次
USE `hm-user`;
CREATE TABLE IF NOT EXISTS `deduct_record` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT,
  `pay_order_id` BIGINT   NOT NULL COMMENT '支付单id，幂等键',
  `user_id`      BIGINT   NOT NULL,
  `amount`       INT      NOT NULL COMMENT '扣款金额，单位分',
  `create_time`  DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_order_id` (`pay_order_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='余额扣款流水';

-- hm-pay：业务订单号唯一约束 + 本地消息表
USE `hm-pay`;
ALTER TABLE `pay_order` ADD UNIQUE KEY `uk_biz_order_no` (`biz_order_no`);

CREATE TABLE IF NOT EXISTS `pay_message` (
  `id`              BIGINT       NOT NULL,
  `biz_order_no`    BIGINT       NOT NULL,
  `exchange`        VARCHAR(64)  NOT NULL,
  `routing_key`     VARCHAR(64)  NOT NULL,
  `payload`         VARCHAR(512) NOT NULL,
  `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0待发送 1已确认 2失败',
  `retry_count`     INT          NOT NULL DEFAULT 0,
  `next_retry_time` DATETIME     NOT NULL,
  `create_time`     DATETIME     NOT NULL,
  `update_time`     DATETIME     NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付通知本地消息表';

-- hm-item：秒杀活动表，库存独立配置，与 item.stock 无关
USE `hm-item`;
CREATE TABLE IF NOT EXISTS `seckill_activity` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT,
  `item_id`       BIGINT   NOT NULL COMMENT '关联商品id',
  `seckill_price` INT      NOT NULL COMMENT '秒杀价，单位分',
  `seckill_stock` INT      NOT NULL COMMENT '秒杀库存，独立配置，与 item.stock 无关',
  `start_time`    DATETIME NOT NULL,
  `end_time`      DATETIME NOT NULL,
  `status`        TINYINT  NOT NULL DEFAULT 0 COMMENT '0未开始 1进行中 2已结束',
  `create_time`   DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_item` (`item_id`),
  KEY `idx_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

-- hm-trade：订单关联秒杀活动；(活动, 用户) 唯一 => 消费幂等 + 一人一单兜底，普通订单该列为 NULL
USE `hm-trade`;
ALTER TABLE `order` ADD COLUMN `seckill_activity_id` BIGINT NULL COMMENT '秒杀活动id，普通订单为 NULL';
ALTER TABLE `order` ADD UNIQUE KEY `uk_seckill_user` (`seckill_activity_id`, `user_id`);
