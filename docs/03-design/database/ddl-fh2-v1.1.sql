-- =============================================================================
-- UAVPlatform · FH2 / 司空 PaaS 接入增量 DDL
-- 版本：v1.1
-- 日期：2026-07-23
-- 说明：在已执行 ddl-v1.0.sql 的库上增量执行
-- =============================================================================

USE `UAVPlatform`;

-- 作业空间绑定司空 workspace（binding_uuid）
ALTER TABLE `t_workspace`
  ADD COLUMN `fh2_binding_uuid` VARCHAR(64) DEFAULT NULL COMMENT '司空 PaaS binding_uuid' AFTER `status`,
  ADD COLUMN `fh2_synced_at` DATETIME(6) DEFAULT NULL COMMENT '最近一次从 FH2 同步时间' AFTER `fh2_binding_uuid`;

-- 设备：网关 SN、子设备关系、FH2 同步标记
ALTER TABLE `t_device`
  ADD COLUMN `gateway_sn` VARCHAR(64) DEFAULT NULL COMMENT '网关/机场 SN（子设备时有值）' AFTER `sn`,
  ADD COLUMN `fh2_device_model_key` VARCHAR(64) DEFAULT NULL COMMENT 'FH2 设备型号枚举键' AFTER `model`,
  ADD COLUMN `source` VARCHAR(32) NOT NULL DEFAULT 'local' COMMENT 'local/fh2' AFTER `work_status`,
  ADD COLUMN `fh2_synced_at` DATETIME(6) DEFAULT NULL COMMENT '最近同步时间' AFTER `online_at`,
  ADD KEY `idx_device_gateway` (`gateway_sn`),
  ADD KEY `idx_device_source` (`source`);

-- 航线：司空航线 UUID（WaylineEditor 保存后回写）
ALTER TABLE `t_wayline`
  ADD COLUMN `fh2_wayline_uuid` VARCHAR(64) DEFAULT NULL COMMENT '司空航线 UUID' AFTER `status`,
  ADD COLUMN `source` VARCHAR(32) NOT NULL DEFAULT 'local' COMMENT 'local/fh2' AFTER `fh2_wayline_uuid`,
  ADD UNIQUE KEY `uk_wayline_fh2_uuid` (`fh2_wayline_uuid`),
  ADD KEY `idx_wayline_source` (`source`);

-- FH2 集成配置（可与 application.yml 并存；库内优先用于动态改配）
CREATE TABLE IF NOT EXISTS `t_fh2_config` (
  `id`                         BIGINT        NOT NULL AUTO_INCREMENT,
  `workspace_id`               BIGINT        NOT NULL DEFAULT 1,
  `enabled`                    TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0关闭',
  `front_host`                 VARCHAR(255)  NOT NULL COMMENT 'paas.js 所在站点，如 http://192.168.41.185',
  `server_host`                VARCHAR(255)  NOT NULL COMMENT '后端 API，如 http://192.168.41.185:30812',
  `gateway_base`               VARCHAR(255)  NOT NULL COMMENT '网关 base，通常 server_host + /gateway',
  `wss_url`                    VARCHAR(512)  NOT NULL COMMENT '推送中心 WS',
  `user_token`                 VARCHAR(2048) DEFAULT NULL COMMENT 'X-User-Token（前端 paas / 后端 SDK 共用）',
  `binding_uuid`               VARCHAR(64)   DEFAULT NULL COMMENT '工作空间 binding_uuid',
  `sdk_address`                VARCHAR(255)  DEFAULT NULL COMMENT 'Java SDK address（scheme+host）',
  `sdk_port`                   VARCHAR(16)   DEFAULT NULL COMMENT 'Java SDK port',
  `wayline_sdk_css`            VARCHAR(512)  DEFAULT NULL,
  `wayline_sdk_js`             VARCHAR(512)  DEFAULT NULL,
  `device_sdk_css`             VARCHAR(512)  DEFAULT NULL,
  `device_sdk_js`              VARCHAR(512)  DEFAULT NULL,
  `extra_http_header_key`      VARCHAR(128)  DEFAULT NULL COMMENT '网关额外鉴权头名，如 x-demo-uuid',
  `extra_http_header_value`    VARCHAR(512)  DEFAULT NULL,
  `created_at`                 DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`                 DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`                    TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fh2_ws` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='司空 PaaS / FH2 SDK 接入配置';

INSERT INTO `t_fh2_config` (
  `workspace_id`, `enabled`, `front_host`, `server_host`, `gateway_base`, `wss_url`,
  `wayline_sdk_css`, `wayline_sdk_js`, `device_sdk_css`, `device_sdk_js`
) VALUES (
  1, 1,
  'http://192.168.41.185',
  'http://192.168.41.185:30812',
  'http://192.168.41.185:30812/gateway',
  'ws://192.168.41.185:30812/openapi/v0.9/notification/ws/v1/notifications',
  'http://192.168.41.185/paasjs-demo/wayline-edit-sdk/index.css',
  'http://192.168.41.185/paasjs-demo/wayline-edit-sdk/index.js',
  'http://192.168.41.185/paasjs-demo/device-manager-sdk/index.css',
  'http://192.168.41.185/paasjs-demo/device-manager-sdk/index.js'
) ON DUPLICATE KEY UPDATE
  `front_host` = VALUES(`front_host`),
  `server_host` = VALUES(`server_host`),
  `gateway_base` = VALUES(`gateway_base`),
  `wss_url` = VALUES(`wss_url`),
  `wayline_sdk_css` = VALUES(`wayline_sdk_css`),
  `wayline_sdk_js` = VALUES(`wayline_sdk_js`),
  `device_sdk_css` = VALUES(`device_sdk_css`),
  `device_sdk_js` = VALUES(`device_sdk_js`);
