-- 校园服务系统数据库初始化脚本
-- 适用于MySQL 8.0.35
-- 创建时间：2025年3月22日

-- 设置字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------
-- 用户相关表
-- -----------------------------------------------------

-- 用户表
CREATE TABLE `user` (
                        `id` BIGINT NOT NULL AUTO_INCREMENT,
                        `is_system_account` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否系统账户',
                        `avatar_url` VARCHAR(255) COMMENT '头像URL',
                        `personal_info` TEXT COMMENT '个人信息(JSON格式)',
                        `user_identity` VARCHAR(50) COMMENT '用户身份(STUDENT/MERCHANT/PLATFORM_STAFF/VERIFIED_USER)',
                        `verification_status` VARCHAR(50) DEFAULT 'UNVERIFIED' COMMENT '验证状态',
                        `username` VARCHAR(100) COMMENT '用户名',
                        `email` VARCHAR(100) COMMENT '邮箱',
                        `encrypted_password` VARCHAR(255) COMMENT '加密密码',
                        `registration_date` DATE COMMENT '注册日期',
                        `birthday` DATE COMMENT '生日',
                        `gender` VARCHAR(20) DEFAULT '未知' COMMENT '性别',
                        `user_group` VARCHAR(50) DEFAULT 'user' COMMENT '用户组',
                        `can_login` BOOLEAN DEFAULT TRUE COMMENT '是否可以登录',
                        `alipay_user_id` VARCHAR(100) COMMENT '支付宝用户ID',
                        PRIMARY KEY (`id`),
                        INDEX `idx_email` (`email`),
                        INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 验证码表
CREATE TABLE `verification_codes` (
                                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                                      `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
                                      `verification_code` VARCHAR(20) NOT NULL COMMENT '验证码',
                                      `send_time` DATETIME NOT NULL COMMENT '发送时间',
                                      PRIMARY KEY (`id`),
                                      INDEX `idx_email_code` (`email`, `verification_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验证码表';

-- -----------------------------------------------------
-- 钱包相关表
-- -----------------------------------------------------

-- 钱包表
CREATE TABLE `wallet` (
                          `id` BIGINT NOT NULL AUTO_INCREMENT,
                          `user_id` BIGINT UNIQUE COMMENT '用户ID',
                          `balance` DECIMAL(10,2) DEFAULT 0.00 COMMENT '余额',
                          `pending_balance` DECIMAL(10,2) DEFAULT 0.00 COMMENT '待处理余额',
                          `pending_balance_release_time` DATETIME COMMENT '待处理余额释放时间',
                          PRIMARY KEY (`id`),
                          CONSTRAINT `fk_wallet_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户钱包表';

-- 钱包审计表
CREATE TABLE `wallet_audit` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT,
                                `wallet_id` BIGINT COMMENT '钱包ID',
                                `user_id` BIGINT COMMENT '用户ID',
                                `action` VARCHAR(50) COMMENT '操作类型',
                                `amount` DECIMAL(10,2) COMMENT '金额',
                                `reason` VARCHAR(255) COMMENT '原因',
                                `performed_by` VARCHAR(100) COMMENT '操作人',
                                `timestamp` DATETIME COMMENT '操作时间',
                                `additional_info` VARCHAR(255) COMMENT '附加信息',
                                PRIMARY KEY (`id`),
                                INDEX `idx_wallet_id` (`wallet_id`),
                                INDEX `idx_user_id` (`user_id`),
                                INDEX `idx_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包审计表';

-- 提现订单表
CREATE TABLE `withdrawal_order` (
                                    `id` BIGINT NOT NULL AUTO_INCREMENT,
                                    `user_id` BIGINT COMMENT '用户ID',
                                    `order_number` VARCHAR(64) NOT NULL UNIQUE COMMENT '订单编号',
                                    `amount` DECIMAL(10,2) NOT NULL COMMENT '金额',
                                    `withdrawal_method` VARCHAR(50) NOT NULL COMMENT '提现方式',
                                    `account_info` VARCHAR(255) NOT NULL COMMENT '账户信息',
                                    `status` VARCHAR(20) NOT NULL COMMENT '状态(PROCESSING/SUCCESS/FAILED)',
                                    `created_time` DATETIME NOT NULL COMMENT '创建时间',
                                    `processed_time` DATETIME COMMENT '处理时间',
                                    `completed_time` DATETIME COMMENT '完成时间',
                                    `alipay_order_id` VARCHAR(100) COMMENT '支付宝订单ID',
                                    `error_message` VARCHAR(500) COMMENT '错误信息',
                                    PRIMARY KEY (`id`),
                                    INDEX `idx_user_id` (`user_id`),
                                    INDEX `idx_order_number` (`order_number`),
                                    INDEX `idx_status` (`status`),
                                    CONSTRAINT `fk_withdrawal_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提现订单表';

-- -----------------------------------------------------
-- 支付相关表
-- -----------------------------------------------------

-- 支付订单表
CREATE TABLE `payment_orders` (
                                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                                  `order_number` VARCHAR(64) NOT NULL UNIQUE COMMENT '订单编号',
                                  `order_type` VARCHAR(30) NOT NULL COMMENT '订单类型',
                                  `order_info` VARCHAR(255) NOT NULL COMMENT '订单信息',
                                  `user_id` BIGINT COMMENT '用户ID',
                                  `amount` DECIMAL(10,2) NOT NULL COMMENT '金额',
                                  `status` VARCHAR(20) NOT NULL COMMENT '支付状态',
                                  `created_time` DATETIME COMMENT '创建时间',
                                  `updated_time` DATETIME COMMENT '更新时间',
                                  `payment_time` DATETIME COMMENT '支付时间',
                                  `expire_time` DATETIME COMMENT '过期时间',
                                  `alipay_trade_no` VARCHAR(100) COMMENT '支付宝交易号',
                                  PRIMARY KEY (`id`),
                                  INDEX `idx_user_id` (`user_id`),
                                  INDEX `idx_order_number` (`order_number`),
                                  INDEX `idx_status` (`status`),
                                  CONSTRAINT `fk_payment_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付订单表';

-- -----------------------------------------------------
-- 消息相关表
-- -----------------------------------------------------

-- 消息表
CREATE TABLE `message` (
                           `id` BIGINT NOT NULL AUTO_INCREMENT,
                           `user_id` BIGINT COMMENT '用户ID',
                           `content` TEXT COMMENT '消息内容',
                           `type` VARCHAR(50) COMMENT '消息类型',
                           `created_date` DATETIME NOT NULL COMMENT '创建时间',
                           `is_read` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已读',
                           `retry_count` INT DEFAULT 0 COMMENT '重试次数',
                           `last_retry_time` DATETIME COMMENT '最后重试时间',
                           `failure_reason` VARCHAR(255) COMMENT '失败原因',
                           PRIMARY KEY (`id`),
                           INDEX `idx_user_id` (`user_id`),
                           INDEX `idx_created_date` (`created_date`),
                           INDEX `idx_is_read` (`is_read`),
                           CONSTRAINT `fk_message_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- -----------------------------------------------------
-- 工单相关表
-- -----------------------------------------------------

-- 工单表
CREATE TABLE `ticket` (
                          `id` BIGINT NOT NULL AUTO_INCREMENT,
                          `issue` VARCHAR(255) COMMENT '问题',
                          `type` INT COMMENT '类型',
                          `created_date` DATETIME COMMENT '创建时间',
                          `closed_date` DATETIME COMMENT '关闭时间',
                          `open` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否开启',
                          `closed_by_admin` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否被管理员关闭',
                          `user_id` BIGINT COMMENT '用户ID',
                          `closed_by_user_id` BIGINT COMMENT '关闭用户ID',
                          `assigned_admin_id` BIGINT COMMENT '分配管理员ID',
                          PRIMARY KEY (`id`),
                          INDEX `idx_user_id` (`user_id`),
                          INDEX `idx_assigned_admin_id` (`assigned_admin_id`),
                          INDEX `idx_open` (`open`),
                          CONSTRAINT `fk_ticket_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
                          CONSTRAINT `fk_ticket_closed_by` FOREIGN KEY (`closed_by_user_id`) REFERENCES `user` (`id`),
                          CONSTRAINT `fk_ticket_assigned` FOREIGN KEY (`assigned_admin_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单表';

-- 聊天记录表
CREATE TABLE `chat` (
                        `id` BIGINT NOT NULL AUTO_INCREMENT,
                        `message` VARCHAR(1000) COMMENT '消息内容',
                        `timestamp` DATETIME COMMENT '时间戳',
                        `ticket_id` BIGINT COMMENT '工单ID',
                        `user_id` BIGINT COMMENT '用户ID',
                        PRIMARY KEY (`id`),
                        INDEX `idx_ticket_id` (`ticket_id`),
                        INDEX `idx_user_id` (`user_id`),
                        CONSTRAINT `fk_chat_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `ticket` (`id`),
                        CONSTRAINT `fk_chat_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天记录表';

-- -----------------------------------------------------
-- 商家相关表
-- -----------------------------------------------------

-- 商家信息表
CREATE TABLE `merchant_info` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT,
                                 `merchant_uid` VARCHAR(50) NOT NULL UNIQUE COMMENT '商家UID',
                                 `primary_user_id` BIGINT NOT NULL COMMENT '主要用户ID',
                                 `business_license` VARCHAR(100) NOT NULL COMMENT '营业执照号',
                                 `license_image` VARCHAR(255) COMMENT '营业执照图片',
                                 `contact_name` VARCHAR(50) NOT NULL COMMENT '联系人姓名',
                                 `contact_phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
                                 `business_address` VARCHAR(255) COMMENT '经营地址',
                                 `merchant_level` VARCHAR(20) DEFAULT 'BRONZE' COMMENT '商家等级',
                                 `total_sales` INT DEFAULT 0 COMMENT '总销售量',
                                 `rating` DOUBLE DEFAULT 5.0 COMMENT '评分',
                                 `created_at` DATETIME NOT NULL COMMENT '创建时间',
                                 `updated_at` DATETIME COMMENT '更新时间',
                                 `verification_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '验证状态',
                                 PRIMARY KEY (`id`),
                                 INDEX `idx_primary_user_id` (`primary_user_id`),
                                 INDEX `idx_merchant_level` (`merchant_level`),
                                 CONSTRAINT `fk_merchant_info_user` FOREIGN KEY (`primary_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家信息表';

-- 商家用户映射表
CREATE TABLE `merchant_user_mapping` (
                                         `id` BIGINT NOT NULL AUTO_INCREMENT,
                                         `merchant_info_id` BIGINT NOT NULL COMMENT '商家信息ID',
                                         `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                         `role` VARCHAR(20) NOT NULL COMMENT '角色',
                                         `created_at` DATETIME NOT NULL COMMENT '创建时间',
                                         `updated_at` DATETIME COMMENT '更新时间',
                                         `invited_by_user_id` BIGINT COMMENT '邀请人ID',
                                         `invitation_accepted` BOOLEAN DEFAULT TRUE COMMENT '邀请是否接受',
                                         PRIMARY KEY (`id`),
                                         UNIQUE KEY `uk_merchant_user` (`merchant_info_id`, `user_id`),
                                         INDEX `idx_user_id` (`user_id`),
                                         CONSTRAINT `fk_merchant_user_mapping_merchant` FOREIGN KEY (`merchant_info_id`) REFERENCES `merchant_info` (`id`),
                                         CONSTRAINT `fk_merchant_user_mapping_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家用户映射表';

-- 商店表
CREATE TABLE `store` (
                         `id` BIGINT NOT NULL AUTO_INCREMENT,
                         `store_name` VARCHAR(100) NOT NULL COMMENT '店铺名称',
                         `description` VARCHAR(500) COMMENT '描述',
                         `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
                         `merchant_info_id` BIGINT COMMENT '商家信息ID',
                         `status` VARCHAR(20) NOT NULL COMMENT '状态',
                         `created_at` DATETIME NOT NULL COMMENT '创建时间',
                         `updated_at` DATETIME COMMENT '更新时间',
                         `contact_phone` VARCHAR(20) COMMENT '联系电话',
                         `business_hours` VARCHAR(100) COMMENT '营业时间',
                         `location` VARCHAR(255) COMMENT '位置',
                         `latitude` DOUBLE COMMENT '纬度',
                         `longitude` DOUBLE COMMENT '经度',
                         `remarks` VARCHAR(500) COMMENT '备注',
                         PRIMARY KEY (`id`),
                         INDEX `idx_merchant_id` (`merchant_id`),
                         INDEX `idx_status` (`status`),
                         CONSTRAINT `fk_store_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `user` (`id`),
                         CONSTRAINT `fk_store_merchant_info` FOREIGN KEY (`merchant_info_id`) REFERENCES `merchant_info` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商店表';

-- 商品表
CREATE TABLE `product` (
                           `id` BIGINT NOT NULL AUTO_INCREMENT,
                           `store_id` BIGINT NOT NULL COMMENT '店铺ID',
                           `name` VARCHAR(100) NOT NULL COMMENT '商品名称',
                           `description` VARCHAR(1000) COMMENT '描述',
                           `price` DECIMAL(10,2) NOT NULL COMMENT '价格',
                           `market_price` DECIMAL(10,2) COMMENT '市场价',
                           `cost_price` DECIMAL(10,2) COMMENT '成本价',
                           `wholesale_price` DECIMAL(10,2) COMMENT '批发价',
                           `stock` INT NOT NULL COMMENT '库存',
                           `weight` DOUBLE NOT NULL COMMENT '重量(kg)',
                           `length` DOUBLE COMMENT '长度(cm)',
                           `width` DOUBLE COMMENT '宽度(cm)',
                           `height` DOUBLE COMMENT '高度(cm)',
                           `status` VARCHAR(20) NOT NULL COMMENT '状态',
                           `category` VARCHAR(30) NOT NULL COMMENT '分类',
                           `image_url` VARCHAR(255) COMMENT '图片URL',
                           `sku_code` VARCHAR(50) UNIQUE COMMENT 'SKU编码',
                           `barcode` VARCHAR(50) COMMENT '条形码',
                           `is_large_item` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否大件商品',
                           `needs_packaging` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否需要包装',
                           `is_fragile` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否易碎',
                           `max_buy_limit` INT COMMENT '最大购买限制',
                           `min_buy_limit` INT NOT NULL DEFAULT 1 COMMENT '最小购买限制',
                           `sales_count` INT NOT NULL DEFAULT 0 COMMENT '销售数量',
                           `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览数量',
                           `rating` DOUBLE NOT NULL DEFAULT 5.0 COMMENT '评分',
                           `review_remark` VARCHAR(500) COMMENT '审核备注',
                           `reviewed_at` DATETIME COMMENT '审核时间',
                           `reviewer_id` BIGINT COMMENT '审核员ID',
                           `created_at` DATETIME NOT NULL COMMENT '创建时间',
                           `updated_at` DATETIME COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           INDEX `idx_store_id` (`store_id`),
                           INDEX `idx_status` (`status`),
                           INDEX `idx_category` (`category`),
                           CONSTRAINT `fk_product_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- -----------------------------------------------------
-- 订单相关表
-- -----------------------------------------------------

-- 购物订单表
CREATE TABLE `shopping_order` (
                                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                                  `order_number` VARCHAR(36) NOT NULL UNIQUE COMMENT '订单编号',
                                  `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                  `store_id` BIGINT NOT NULL COMMENT '店铺ID',
                                  `product_id` BIGINT NOT NULL COMMENT '商品ID',
                                  `quantity` INT NOT NULL COMMENT '数量',
                                  `product_volume` DOUBLE COMMENT '商品体积',
                                  `product_price` DECIMAL(10,2) NOT NULL COMMENT '商品价格',
                                  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '总金额',
                                  `delivery_fee` DECIMAL(10,2) NOT NULL COMMENT '配送费',
                                  `service_fee` DECIMAL(10,2) NOT NULL COMMENT '服务费',
                                  `platform_fee` DECIMAL(10,2) NOT NULL COMMENT '平台费',
                                  `merchant_income` DECIMAL(10,2) NOT NULL COMMENT '商家收入',
                                  `delivery_type` VARCHAR(20) NOT NULL COMMENT '配送类型',
                                  `order_status` VARCHAR(20) NOT NULL COMMENT '订单状态',
                                  `recipient_name` VARCHAR(50) NOT NULL COMMENT '收件人姓名',
                                  `recipient_phone` VARCHAR(20) NOT NULL COMMENT '收件人电话',
                                  `delivery_address` VARCHAR(255) NOT NULL COMMENT '配送地址',
                                  `delivery_latitude` DOUBLE COMMENT '配送纬度',
                                  `delivery_longitude` DOUBLE COMMENT '配送经度',
                                  `delivery_distance` DOUBLE COMMENT '配送距离',
                                  `assigned_user_id` BIGINT COMMENT '分配用户ID',
                                  `expected_delivery_time` DATETIME COMMENT '预期配送时间',
                                  `delivered_time` DATETIME COMMENT '送达时间',
                                  `remark` VARCHAR(255) COMMENT '备注',
                                  `created_at` DATETIME NOT NULL COMMENT '创建时间',
                                  `updated_at` DATETIME COMMENT '更新时间',
                                  `payment_time` DATETIME COMMENT '支付时间',
                                  `refund_status` VARCHAR(20) COMMENT '退款状态',
                                  `refund_amount` DECIMAL(10,2) COMMENT '退款金额',
                                  `refund_time` DATETIME COMMENT '退款时间',
                                  `refund_requested_at` DATETIME COMMENT '退款请求时间',
                                  `refund_reason` VARCHAR(255) COMMENT '退款原因',
                                  `timeout_status` VARCHAR(20) DEFAULT 'NORMAL' COMMENT '超时状态',
                                  `timeout_warning_sent` BOOLEAN DEFAULT FALSE COMMENT '是否发送超时警告',
                                  `timeout_count` INT NOT NULL DEFAULT 0 COMMENT '超时次数',
                                  `intervention_time` DATETIME COMMENT '干预时间',
                                  PRIMARY KEY (`id`),
                                  INDEX `idx_order_number` (`order_number`),
                                  INDEX `idx_user_id` (`user_id`),
                                  INDEX `idx_store_id` (`store_id`),
                                  INDEX `idx_product_id` (`product_id`),
                                  INDEX `idx_order_status` (`order_status`),
                                  INDEX `idx_created_at` (`created_at`),
                                  CONSTRAINT `fk_shopping_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
                                  CONSTRAINT `fk_shopping_order_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
                                  CONSTRAINT `fk_shopping_order_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
                                  CONSTRAINT `fk_shopping_order_assigned_user` FOREIGN KEY (`assigned_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物订单表';

-- 代购请求表
CREATE TABLE `purchase_request` (
                                    `id` BIGINT NOT NULL AUTO_INCREMENT,
                                    `request_number` VARCHAR(36) NOT NULL UNIQUE COMMENT '请求编号',
                                    `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                    `title` VARCHAR(100) NOT NULL COMMENT '标题',
                                    `description` VARCHAR(1000) COMMENT '描述',
                                    `category` VARCHAR(30) COMMENT '分类',
                                    `expected_price` DECIMAL(10,2) NOT NULL COMMENT '预期价格',
                                    `image_url` VARCHAR(255) COMMENT '图片URL',
                                    `purchase_address` VARCHAR(255) NOT NULL COMMENT '购买地址',
                                    `purchase_latitude` DOUBLE NOT NULL COMMENT '购买纬度',
                                    `purchase_longitude` DOUBLE NOT NULL COMMENT '购买经度',
                                    `delivery_address` VARCHAR(255) NOT NULL COMMENT '配送地址',
                                    `delivery_latitude` DOUBLE COMMENT '配送纬度',
                                    `delivery_longitude` DOUBLE COMMENT '配送经度',
                                    `delivery_distance` DOUBLE COMMENT '配送距离',
                                    `recipient_name` VARCHAR(50) NOT NULL COMMENT '收件人姓名',
                                    `recipient_phone` VARCHAR(20) NOT NULL COMMENT '收件人电话',
                                    `delivery_type` VARCHAR(20) NOT NULL COMMENT '配送类型',
                                    `assigned_user_id` BIGINT COMMENT '分配用户ID',
                                    `deadline` DATETIME COMMENT '截止时间',
                                    `delivery_time` DATETIME COMMENT '配送时间',
                                    `delivered_date` DATETIME COMMENT '送达时间',
                                    `created_at` DATETIME NOT NULL COMMENT '创建时间',
                                    `updated_at` DATETIME NOT NULL COMMENT '更新时间',
                                    `payment_time` DATETIME COMMENT '支付时间',
                                    `completion_date` DATETIME COMMENT '完成时间',
                                    `status` VARCHAR(20) NOT NULL COMMENT '状态',
                                    `delivery_fee` DECIMAL(10,2) COMMENT '配送费',
                                    `service_fee` DECIMAL(10,2) COMMENT '服务费',
                                    `total_amount` DECIMAL(10,2) COMMENT '总金额',
                                    `user_income` DOUBLE COMMENT '用户收入',
                                    `platform_income` DOUBLE COMMENT '平台收入',
                                    `refund_status` VARCHAR(20) COMMENT '退款状态',
                                    `refund_amount` DECIMAL(10,2) COMMENT '退款金额',
                                    `refund_reason` VARCHAR(255) COMMENT '退款原因',
                                    `refund_requested_at` DATETIME COMMENT '退款请求时间',
                                    `refund_date` DATETIME COMMENT '退款时间',
                                    `timeout_status` VARCHAR(20) DEFAULT 'NORMAL' COMMENT '超时状态',
                                    `timeout_warning_sent` BOOLEAN DEFAULT FALSE COMMENT '是否发送超时警告',
                                    `timeout_count` INT NOT NULL DEFAULT 0 COMMENT '超时次数',
                                    `intervention_time` DATETIME COMMENT '干预时间',
                                    `weight` DOUBLE COMMENT '重量',
                                    `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览次数',
                                    PRIMARY KEY (`id`),
                                    INDEX `idx_request_number` (`request_number`),
                                    INDEX `idx_user_id` (`user_id`),
                                    INDEX `idx_status` (`status`),
                                    INDEX `idx_created_at` (`created_at`),
                                    CONSTRAINT `fk_purchase_request_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
                                    CONSTRAINT `fk_purchase_request_assigned_user` FOREIGN KEY (`assigned_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代购请求表';

-- 快递订单表
CREATE TABLE `mail_order` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `order_number` VARCHAR(36) NOT NULL UNIQUE COMMENT '订单编号',
                              `created_at` DATETIME COMMENT '创建时间',
                              `name` VARCHAR(100) COMMENT '名称',
                              `order_status` VARCHAR(20) COMMENT '订单状态',
                              `timeout_status` VARCHAR(20) DEFAULT 'NORMAL' COMMENT '超时状态',
                              `timeout_warning_sent` BOOLEAN DEFAULT FALSE COMMENT '是否发送超时警告',
                              `timeout_count` INT NOT NULL DEFAULT 0 COMMENT '超时次数',
                              `user_id` BIGINT COMMENT '用户ID',
                              `assigned_user_id` BIGINT COMMENT '分配用户ID',
                              `pickup_code` VARCHAR(50) COMMENT '取件码',
                              `pickup_address` VARCHAR(255) COMMENT '取件地址',
                              `pickup_latitude` DOUBLE COMMENT '取件纬度',
                              `pickup_longitude` DOUBLE COMMENT '取件经度',
                              `pickup_detail` VARCHAR(255) COMMENT '取件详情',
                              `delivery_address` VARCHAR(255) COMMENT '配送地址',
                              `delivery_latitude` DOUBLE COMMENT '配送纬度',
                              `delivery_longitude` DOUBLE COMMENT '配送经度',
                              `delivery_detail` VARCHAR(255) COMMENT '配送详情',
                              `delivery_distance` DOUBLE COMMENT '配送距离',
                              `tracking_number` VARCHAR(50) COMMENT '快递单号',
                              `contact_info` VARCHAR(100) COMMENT '联系信息',
                              `delivery_time` DATETIME COMMENT '配送时间',
                              `delivered_date` DATETIME COMMENT '送达时间',
                              `completion_date` DATETIME COMMENT '完成时间',
                              `intervention_time` DATETIME COMMENT '干预时间',
                              `refund_requested_at` DATETIME COMMENT '退款请求时间',
                              `refund_date` DATETIME COMMENT '退款时间',
                              `delivery_service` VARCHAR(20) COMMENT '配送服务',
                              `weight` DOUBLE COMMENT '重量',
                              `is_large_item` BOOLEAN DEFAULT FALSE COMMENT '是否大件',
                              `fee` DOUBLE COMMENT '费用',
                              `user_income` DOUBLE COMMENT '用户收入',
                              `platform_income` DOUBLE COMMENT '平台收入',
                              `region_multiplier` DOUBLE DEFAULT 1.0 COMMENT '区域倍数',
                              `lock_reason` VARCHAR(255) COMMENT '锁定原因',
                              PRIMARY KEY (`id`),
                              INDEX `idx_order_number` (`order_number`),
                              INDEX `idx_user_id` (`user_id`),
                              INDEX `idx_assigned_user_id` (`assigned_user_id`),
                              INDEX `idx_order_status` (`order_status`),
                              INDEX `idx_created_at` (`created_at`),
                              CONSTRAINT `fk_mail_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
                              CONSTRAINT `fk_mail_order_assigned_user` FOREIGN KEY (`assigned_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='快递订单表';

-- 废弃订单表
CREATE TABLE `abandoned_order` (
                                   `id` BIGINT NOT NULL AUTO_INCREMENT,
                                   `order_number` VARCHAR(36) NOT NULL UNIQUE COMMENT '订单编号',
                                   `user_id` BIGINT COMMENT '用户ID',
                                   `name` VARCHAR(100) COMMENT '名称',
                                   `pickup_code` VARCHAR(50) COMMENT '取件码',
                                   `tracking_number` VARCHAR(50) COMMENT '快递单号',
                                   `contact_info` VARCHAR(100) COMMENT '联系信息',
                                   `pickup_address` VARCHAR(255) COMMENT '取件地址',
                                   `pickup_latitude` DOUBLE COMMENT '取件纬度',
                                   `pickup_longitude` DOUBLE COMMENT '取件经度',
                                   `pickup_detail` VARCHAR(255) COMMENT '取件详情',
                                   `delivery_address` VARCHAR(255) COMMENT '配送地址',
                                   `delivery_latitude` DOUBLE COMMENT '配送纬度',
                                   `delivery_longitude` DOUBLE COMMENT '配送经度',
                                   `delivery_detail` VARCHAR(255) COMMENT '配送详情',
                                   `delivery_time` DATETIME COMMENT '配送时间',
                                   `created_at` DATETIME COMMENT '创建时间',
                                   `closed_at` DATETIME COMMENT '关闭时间',
                                   `intervention_time` DATETIME COMMENT '干预时间',
                                   `refund_requested_at` DATETIME COMMENT '退款请求时间',
                                   `refund_date` DATETIME COMMENT '退款时间',
                                   `completion_date` DATETIME COMMENT '完成时间',
                                   `delivered_date` DATETIME COMMENT '送达时间',
                                   `delivery_service` VARCHAR(20) COMMENT '配送服务',
                                   `order_status` VARCHAR(20) COMMENT '订单状态',
                                   `timeout_status` VARCHAR(20) COMMENT '超时状态',
                                   `timeout_warning_sent` BOOLEAN DEFAULT FALSE COMMENT '是否发送超时警告',
                                   `weight` DOUBLE COMMENT '重量',
                                   `is_large_item` BOOLEAN DEFAULT FALSE COMMENT '是否大件',
                                   `fee` DOUBLE COMMENT '费用',
                                   `user_income` DOUBLE COMMENT '用户收入',
                                   `platform_income` DOUBLE COMMENT '平台收入',
                                   `lock_reason` VARCHAR(255) COMMENT '锁定原因',
                                   `region_multiplier` DOUBLE DEFAULT 1.0 COMMENT '区域倍数',
                                   `delivery_distance` DOUBLE COMMENT '配送距离',
                                   `pickup_region_name` VARCHAR(50) COMMENT '取件区域名称',
                                   `delivery_region_name` VARCHAR(50) COMMENT '配送区域名称',
                                   `is_cross_region` BOOLEAN DEFAULT FALSE COMMENT '是否跨区域',
                                   `time_range_name` VARCHAR(50) COMMENT '时间范围名称',
                                   `time_range_rate` DECIMAL(10,2) COMMENT '时间范围费率',
                                   `special_date_name` VARCHAR(50) COMMENT '特殊日期名称',
                                   `special_date_type` VARCHAR(30) COMMENT '特殊日期类型',
                                   `special_date_rate` DECIMAL(10,2) COMMENT '特殊日期费率',
                                   `rater_id` BIGINT COMMENT '评价人ID',
                                   `rated_user_id` BIGINT COMMENT '被评价人ID',
                                   `rating_comment` VARCHAR(1000) COMMENT '评价内容',
                                   `rating_score` INT COMMENT '评分',
                                   `rating_date` DATETIME COMMENT '评价日期',
                                   `rating_type` VARCHAR(30) COMMENT '评价类型',
                                   `assigned_user_id` BIGINT COMMENT '分配用户ID',
                                   `timeout_count` INT NOT NULL DEFAULT 0 COMMENT '超时次数',
                                   PRIMARY KEY (`id`),
                                   INDEX `idx_order_number` (`order_number`),
                                   INDEX `idx_user_id` (`user_id`),
                                   INDEX `idx_assigned_user_id` (`assigned_user_id`),
                                   INDEX `idx_order_status` (`order_status`),
                                   CONSTRAINT `fk_abandoned_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
                                   CONSTRAINT `fk_abandoned_order_assigned_user` FOREIGN KEY (`assigned_user_id`) REFERENCES `user` (`id`),
                                   CONSTRAINT `fk_abandoned_order_rater` FOREIGN KEY (`rater_id`) REFERENCES `user` (`id`),
                                   CONSTRAINT `fk_abandoned_order_rated_user` FOREIGN KEY (`rated_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='废弃订单表';

-- -----------------------------------------------------
-- 评价相关表
-- -----------------------------------------------------

-- 评价表
CREATE TABLE `ratings` (
                           `id` BIGINT NOT NULL AUTO_INCREMENT,
                           `rater_id` BIGINT COMMENT '评价人ID',
                           `rated_user_id` BIGINT COMMENT '被评价人ID',
                           `order_type` VARCHAR(30) NOT NULL COMMENT '订单类型',
                           `order_number` VARCHAR(36) NOT NULL COMMENT '订单编号',
                           `comment` VARCHAR(1000) COMMENT '评价内容',
                           `score` INT NOT NULL COMMENT '评分',
                           `rating_date` DATETIME NOT NULL COMMENT '评价日期',
                           `rating_type` VARCHAR(30) NOT NULL COMMENT '评价类型',
                           PRIMARY KEY (`id`),
                           INDEX `idx_rater_id` (`rater_id`),
                           INDEX `idx_rated_user_id` (`rated_user_id`),
                           INDEX `idx_order_number` (`order_number`),
                           CONSTRAINT `fk_rating_rater` FOREIGN KEY (`rater_id`) REFERENCES `user` (`id`),
                           CONSTRAINT `fk_rating_rated_user` FOREIGN KEY (`rated_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价表';

-- -----------------------------------------------------
-- 超时管理相关表
-- -----------------------------------------------------

-- 全局超时报告表
CREATE TABLE `global_timeout_report` (
                                         `report_id` VARCHAR(36) NOT NULL COMMENT '报告ID',
                                         `generated_time` DATETIME NOT NULL COMMENT '生成时间',
                                         `start_time` DATETIME COMMENT '开始时间',
                                         `end_time` DATETIME COMMENT '结束时间',
                                         `system_stats` JSON COMMENT '系统统计',
                                         `top_timeout_users` JSON COMMENT '超时用户排名',
                                         PRIMARY KEY (`report_id`),
                                         INDEX `idx_generated_time` (`generated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局超时报告表';

-- 全局超时报告建议表
CREATE TABLE `global_timeout_report_recommendations` (
                                                         `report_id` VARCHAR(36) NOT NULL COMMENT '报告ID',
                                                         `recommendation` VARCHAR(255) COMMENT '建议',
                                                         INDEX `idx_report_id` (`report_id`),
                                                         CONSTRAINT `fk_timeout_recommendation_report` FOREIGN KEY (`report_id`) REFERENCES `global_timeout_report` (`report_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局超时报告建议表';

-- -----------------------------------------------------
-- 营销相关表
-- -----------------------------------------------------

-- 特殊日期表
CREATE TABLE `special_date` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT,
                                `name` VARCHAR(50) NOT NULL COMMENT '名称',
                                `date` DATE NOT NULL COMMENT '日期',
                                `rate_multiplier` DECIMAL(10,2) NOT NULL COMMENT '费率倍数',
                                `description` VARCHAR(255) COMMENT '描述',
                                `type` VARCHAR(20) NOT NULL COMMENT '类型',
                                `active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否激活',
                                `create_time` DATETIME NOT NULL COMMENT '创建时间',
                                `update_time` DATETIME COMMENT '更新时间',
                                `rate_enabled` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '费率是否启用',
                                `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级',
                                `fee_type` VARCHAR(20) NOT NULL COMMENT '费用类型',
                                PRIMARY KEY (`id`),
                                INDEX `idx_date` (`date`),
                                INDEX `idx_active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='特殊日期表';

-- 特殊时间范围表
CREATE TABLE `special_time_range` (
                                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                                      `name` VARCHAR(50) NOT NULL COMMENT '名称',
                                      `start_hour` INT NOT NULL COMMENT '开始小时',
                                      `end_hour` INT NOT NULL COMMENT '结束小时',
                                      `rate_multiplier` DECIMAL(10,2) NOT NULL DEFAULT 1.00 COMMENT '费率倍数',
                                      `description` VARCHAR(255) COMMENT '描述',
                                      `active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否激活',
                                      `create_time` DATETIME NOT NULL COMMENT '创建时间',
                                      `update_time` DATETIME COMMENT '更新时间',
                                      `fee_type` VARCHAR(20) NOT NULL COMMENT '费用类型',
                                      PRIMARY KEY (`id`),
                                      INDEX `idx_active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='特殊时间范围表';

-- 配送区域表
CREATE TABLE `delivery_region` (
                                   `id` BIGINT NOT NULL AUTO_INCREMENT,
                                   `name` VARCHAR(50) NOT NULL COMMENT '名称',
                                   `description` VARCHAR(255) COMMENT '描述',
                                   `rate_multiplier` DOUBLE NOT NULL DEFAULT 1.0 COMMENT '费率倍数',
                                   `active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否激活',
                                   `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级',
                                   PRIMARY KEY (`id`),
                                   INDEX `idx_active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配送区域表';

-- -----------------------------------------------------
-- 电脑问题相关表
-- -----------------------------------------------------

-- 问题表
CREATE TABLE `questions` (
                             `id` BIGINT NOT NULL AUTO_INCREMENT,
                             `question_type` VARCHAR(30) NOT NULL COMMENT '问题类型',
                             `image_url` VARCHAR(255) COMMENT '图片URL',
                             `description` TEXT NOT NULL COMMENT '描述',
                             `short_title` VARCHAR(30) COMMENT '短标题',
                             `contact_info` VARCHAR(100) NOT NULL COMMENT '联系信息',
                             `contact_name` VARCHAR(50) NOT NULL COMMENT '联系人姓名',
                             `user_id` BIGINT NOT NULL COMMENT '用户ID',
                             `created_at` DATETIME NOT NULL COMMENT '创建时间',
                             `updated_at` DATETIME COMMENT '更新时间',
                             `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态',
                             `accepted_user_id` BIGINT COMMENT '接受用户ID',
                             `resolved_at` DATETIME COMMENT '解决时间',
                             `closed_at` DATETIME COMMENT '关闭时间',
                             `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览次数',
                             `version` BIGINT COMMENT '版本',
                             PRIMARY KEY (`id`),
                             INDEX `idx_user_id` (`user_id`),
                             INDEX `idx_status` (`status`),
                             INDEX `idx_question_type` (`question_type`),
                             INDEX `idx_created_at` (`created_at`),
                             CONSTRAINT `fk_question_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
                             CONSTRAINT `fk_question_accepted_user` FOREIGN KEY (`accepted_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问题表';

-- 问题回复表
CREATE TABLE `question_replies` (
                                    `id` BIGINT NOT NULL AUTO_INCREMENT,
                                    `question_id` BIGINT NOT NULL COMMENT '问题ID',
                                    `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                    `content` TEXT NOT NULL COMMENT '内容',
                                    `created_at` DATETIME NOT NULL COMMENT '创建时间',
                                    `updated_at` DATETIME COMMENT '更新时间',
                                    `applied` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已申请',
                                    `is_rejected` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否被拒绝',
                                    `version` BIGINT COMMENT '版本',
                                    PRIMARY KEY (`id`),
                                    INDEX `idx_question_id` (`question_id`),
                                    INDEX `idx_user_id` (`user_id`),
                                    INDEX `idx_created_at` (`created_at`),
                                    CONSTRAINT `fk_question_reply_question` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`),
                                    CONSTRAINT `fk_question_reply_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问题回复表';

-- -----------------------------------------------------
-- 搭子相关表
-- -----------------------------------------------------

-- 好友资料表
CREATE TABLE `friend_profile` (
                                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                                  `user_id` BIGINT UNIQUE COMMENT '用户ID',
                                  `hobbies` JSON COMMENT '兴趣爱好',
                                  `latitude` DOUBLE COMMENT '纬度',
                                  `longitude` DOUBLE COMMENT '经度',
                                  `university` VARCHAR(100) COMMENT '大学',
                                  `study_subjects` JSON COMMENT '学习科目',
                                  `sports` JSON COMMENT '运动',
                                  `preferred_match_type` VARCHAR(20) COMMENT '首选匹配类型',
                                  `contact_type` VARCHAR(20) COMMENT '联系方式类型',
                                  `contact_number` VARCHAR(100) COMMENT '联系方式',
                                  `created_at` DATETIME COMMENT '创建时间',
                                  `updated_at` DATETIME COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  INDEX `idx_user_id` (`user_id`),
                                  INDEX `idx_university` (`university`),
                                  INDEX `idx_preferred_match_type` (`preferred_match_type`),
                                  CONSTRAINT `fk_friend_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友资料表';

-- 好友匹配表
CREATE TABLE `friend_match` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT,
                                `requester_id` BIGINT COMMENT '请求者ID',
                                `target_id` BIGINT COMMENT '目标ID',
                                `match_score` DOUBLE COMMENT '匹配分数',
                                `match_points` JSON COMMENT '匹配点',
                                `match_status` VARCHAR(20) COMMENT '匹配状态',
                                `created_at` DATETIME COMMENT '创建时间',
                                `updated_at` DATETIME COMMENT '更新时间',
                                PRIMARY KEY (`id`),
                                INDEX `idx_requester_id` (`requester_id`),
                                INDEX `idx_target_id` (`target_id`),
                                INDEX `idx_match_status` (`match_status`),
                                CONSTRAINT `fk_friend_match_requester` FOREIGN KEY (`requester_id`) REFERENCES `user` (`id`),
                                CONSTRAINT `fk_friend_match_target` FOREIGN KEY (`target_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友匹配表';

-- 游戏技能表
CREATE TABLE `game_skill` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT,
                              `friend_id` BIGINT COMMENT '好友ID',
                              `game_name` VARCHAR(100) COMMENT '游戏名称',
                              `skill_level` VARCHAR(20) COMMENT '技能等级',
                              `rank` VARCHAR(50) COMMENT '段位',
                              `preferred_position` VARCHAR(50) COMMENT '首选位置',
                              PRIMARY KEY (`id`),
                              INDEX `idx_friend_id` (`friend_id`),
                              INDEX `idx_game_name` (`game_name`),
                              CONSTRAINT `fk_game_skill_friend` FOREIGN KEY (`friend_id`) REFERENCES `friend_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏技能表';

-- 才能表
CREATE TABLE `talent` (
                          `id` BIGINT NOT NULL AUTO_INCREMENT,
                          `friend_id` BIGINT COMMENT '好友ID',
                          `talent_name` VARCHAR(100) COMMENT '才能名称',
                          `proficiency` VARCHAR(20) COMMENT '熟练度',
                          `certification` VARCHAR(100) COMMENT '认证',
                          `can_teach` BOOLEAN COMMENT '能否教授',
                          PRIMARY KEY (`id`),
                          INDEX `idx_friend_id` (`friend_id`),
                          INDEX `idx_talent_name` (`talent_name`),
                          CONSTRAINT `fk_talent_friend` FOREIGN KEY (`friend_id`) REFERENCES `friend_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='才能表';

-- 时间段表
CREATE TABLE `time_slot` (
                             `id` BIGINT NOT NULL AUTO_INCREMENT,
                             `friend_id` BIGINT COMMENT '好友ID',
                             `day_of_week` VARCHAR(20) COMMENT '星期几',
                             `start_time` TIME COMMENT '开始时间',
                             `end_time` TIME COMMENT '结束时间',
                             PRIMARY KEY (`id`),
                             INDEX `idx_friend_id` (`friend_id`),
                             CONSTRAINT `fk_time_slot_friend` FOREIGN KEY (`friend_id`) REFERENCES `friend_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时间段表';

-- 旅行目的地表
CREATE TABLE `travel_destination` (
                                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                                      `friend_id` BIGINT COMMENT '好友ID',
                                      `destination` VARCHAR(100) COMMENT '目的地',
                                      `province` VARCHAR(50) COMMENT '省份',
                                      `country` VARCHAR(50) COMMENT '国家',
                                      `travel_type` VARCHAR(20) COMMENT '旅行类型',
                                      `expected_season` VARCHAR(20) COMMENT '预期季节',
                                      PRIMARY KEY (`id`),
                                      INDEX `idx_friend_id` (`friend_id`),
                                      INDEX `idx_destination` (`destination`),
                                      CONSTRAINT `fk_travel_destination_friend` FOREIGN KEY (`friend_id`) REFERENCES `friend_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旅行目的地表';

SET FOREIGN_KEY_CHECKS = 1;