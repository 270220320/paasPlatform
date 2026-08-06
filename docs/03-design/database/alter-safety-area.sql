-- 空域安全：限飞区与设备同步状态（本地持久化）
CREATE TABLE IF NOT EXISTS `t_safety_area` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`     BIGINT         NOT NULL DEFAULT 1,
  `name`             VARCHAR(128)   NOT NULL COMMENT '限飞区名称',
  `zone_type`        TINYINT        NOT NULL COMMENT '3=多边形 4=圆形',
  `content`          JSON           NOT NULL COMMENT 'GeoJSON Feature 内容',
  `color`            VARCHAR(32)    DEFAULT '#E23C39',
  `line_width`       INT            DEFAULT 4,
  `radius_m`         DECIMAL(14,2)  DEFAULT NULL COMMENT '圆形半径米',
  `clamp_to_ground`  TINYINT        NOT NULL DEFAULT 1,
  `sync_status`      VARCHAR(32)    NOT NULL DEFAULT 'pending' COMMENT 'pending/synced/failed',
  `created_by`       BIGINT         DEFAULT NULL,
  `updated_by`       BIGINT         DEFAULT NULL,
  `created_at`       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`          TINYINT        NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_safety_area_ws` (`workspace_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空域限飞区';

CREATE TABLE IF NOT EXISTS `t_safety_device_sync` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`  BIGINT       NOT NULL DEFAULT 1,
  `device_sn`     VARCHAR(64)  NOT NULL COMMENT '机场/设备 SN',
  `nickname`      VARCHAR(128) DEFAULT NULL,
  `sync_status`   VARCHAR(32)  NOT NULL DEFAULT 'wait_sync' COMMENT 'wait_sync/synchronizing/synchronized/fail',
  `sync_msg`      VARCHAR(500) DEFAULT NULL,
  `zone_hash`     VARCHAR(128) DEFAULT NULL COMMENT '限飞区内容指纹',
  `updated_at`    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `created_at`    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_safety_sync_ws_sn` (`workspace_id`, `device_sn`),
  KEY `idx_safety_sync_status` (`workspace_id`, `sync_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空域限飞区设备同步状态';
