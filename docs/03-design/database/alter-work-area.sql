-- 空域安全：作业区（本地持久化，不推送司空）
CREATE TABLE IF NOT EXISTS `t_work_area` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`     BIGINT         NOT NULL DEFAULT 1,
  `name`             VARCHAR(128)   NOT NULL COMMENT '作业区名称',
  `zone_type`        TINYINT        NOT NULL COMMENT '3=多边形 4=圆形',
  `content`          JSON           NOT NULL COMMENT 'GeoJSON Feature 内容',
  `color`            VARCHAR(32)    DEFAULT '#2EB85C' COMMENT '默认绿色',
  `line_width`       INT            DEFAULT 4,
  `radius_m`         DECIMAL(14,2)  DEFAULT NULL COMMENT '圆形半径米',
  `clamp_to_ground`  TINYINT        NOT NULL DEFAULT 1,
  `min_altitude`     DECIMAL(10,2)  DEFAULT NULL COMMENT '最低飞行高度(米)',
  `max_altitude`     DECIMAL(10,2)  DEFAULT NULL COMMENT '最高飞行高度(米)',
  `description`      VARCHAR(500)   DEFAULT NULL COMMENT '备注描述',
  `status`           VARCHAR(32)    NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
  `created_by`       BIGINT         DEFAULT NULL,
  `updated_by`       BIGINT         DEFAULT NULL,
  `created_at`       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`          TINYINT        NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_work_area_ws` (`workspace_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业区';
