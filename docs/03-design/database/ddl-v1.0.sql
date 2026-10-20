-- =============================================================================
-- AiFlow 单作业空间 · 建表 DDL
-- 版本：v1.0
-- 生成日期：2026-07-21
-- 数据库类型：MySQL 8.x
-- 字符集：utf8mb4 / utf8mb4_unicode_ci
-- 引擎：InnoDB
-- 说明：执行前请确认库名；建议先在测试环境执行
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `UAVPlatform`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `UAVPlatform`;

SET NAMES utf8mb4;

-- =============================================================================
-- 0. 作业空间（单实例配置，预留多租户键）
-- =============================================================================
CREATE TABLE `t_workspace` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_code`  VARCHAR(64)  NOT NULL COMMENT '作业空间编码',
  `name`            VARCHAR(128) NOT NULL COMMENT '名称',
  `status`          VARCHAR(32)  NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
  `created_at`      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `updated_at`      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workspace_code` (`workspace_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业空间';

-- =============================================================================
-- 1. IAM
-- =============================================================================
CREATE TABLE `t_permission` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`        VARCHAR(128) NOT NULL COMMENT '权限编码 如 map:write',
  `module`      VARCHAR(64)  NOT NULL COMMENT '模块 dashboard/map/wayline/...',
  `action`      VARCHAR(32)  NOT NULL COMMENT 'read/write/manage',
  `name`        VARCHAR(64)  NOT NULL COMMENT '显示名',
  `created_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`code`),
  KEY `idx_permission_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限点';

CREATE TABLE `t_role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`        VARCHAR(64)  NOT NULL COMMENT '角色编码',
  `name`        VARCHAR(64)  NOT NULL COMMENT '角色名称',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '说明',
  `is_system`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否系统预置',
  `locked`      TINYINT      NOT NULL DEFAULT 0 COMMENT '权限是否锁定不可改',
  `created_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色';

CREATE TABLE `t_role_permission` (
  `role_id`       BIGINT NOT NULL COMMENT '角色ID',
  `permission_id` BIGINT NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`role_id`, `permission_id`),
  KEY `idx_rp_permission` (`permission_id`),
  CONSTRAINT `fk_rp_role` FOREIGN KEY (`role_id`) REFERENCES `t_role` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_rp_permission` FOREIGN KEY (`permission_id`) REFERENCES `t_permission` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联';

CREATE TABLE `t_user` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`   BIGINT       NOT NULL DEFAULT 1 COMMENT '作业空间ID',
  `account`        VARCHAR(128) NOT NULL COMMENT '登录账号',
  `display_name`   VARCHAR(64)  NOT NULL COMMENT '显示名称',
  `password_hash`  VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `status`         VARCHAR(32)  NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
  `join_method`    VARCHAR(32)  NOT NULL DEFAULT 'organization_add' COMMENT '加入方式',
  `joined_at`      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '加入时间',
  `last_login_at`  DATETIME(6)  DEFAULT NULL COMMENT '最近登录',
  `role_id`        BIGINT       NOT NULL COMMENT '角色ID',
  `created_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`account`),
  KEY `idx_user_role` (`role_id`),
  KEY `idx_user_workspace_status` (`workspace_id`, `status`),
  CONSTRAINT `fk_user_role` FOREIGN KEY (`role_id`) REFERENCES `t_role` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_user_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `t_workspace` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';

-- =============================================================================
-- 2. Device
-- =============================================================================
CREATE TABLE `t_device` (
  `id`                         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`               BIGINT       NOT NULL DEFAULT 1 COMMENT '作业空间ID',
  `sn`                         VARCHAR(64)  NOT NULL COMMENT '设备序列号',
  `name`                       VARCHAR(128) NOT NULL COMMENT '设备名称',
  `device_type`                VARCHAR(32)  NOT NULL COMMENT 'dock/aircraft/other',
  `model`                      VARCHAR(128) NOT NULL COMMENT '型号',
  `firmware_version`           VARCHAR(64)  DEFAULT NULL COMMENT '固件版本',
  `firmware_upgrade_status`    VARCHAR(32)  DEFAULT NULL COMMENT 'latest/upgradable',
  `flight_safety_db_version`   VARCHAR(64)  DEFAULT NULL COMMENT '飞行安全数据库版本',
  `work_status`                VARCHAR(32)  NOT NULL DEFAULT 'offline' COMMENT 'idle/working/debugging/offline/in_dock',
  `joined_at`                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '加入时间',
  `online_at`                  DATETIME(6)  DEFAULT NULL COMMENT '最近在线时间',
  `created_at`                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`                    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_sn` (`sn`),
  KEY `idx_device_type_status` (`device_type`, `work_status`),
  KEY `idx_device_workspace` (`workspace_id`),
  CONSTRAINT `fk_device_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `t_workspace` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备';

CREATE TABLE `t_device_status` (
  `device_id`          BIGINT       NOT NULL COMMENT '设备ID',
  `online_state`       VARCHAR(32)  NOT NULL DEFAULT 'offline' COMMENT 'online/offline',
  `work_sub_status`    VARCHAR(64)  DEFAULT NULL COMMENT '细分状态',
  `telemetry_snapshot` JSON         DEFAULT NULL COMMENT '遥测快照',
  `updated_at`         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`device_id`),
  CONSTRAINT `fk_device_status_device` FOREIGN KEY (`device_id`) REFERENCES `t_device` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备运行状态';

CREATE TABLE `t_bind_code` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`  BIGINT       NOT NULL DEFAULT 1 COMMENT '作业空间ID',
  `code`          VARCHAR(64)  NOT NULL COMMENT '绑定码',
  `status`        VARCHAR(32)  NOT NULL DEFAULT 'active' COMMENT 'active/used/expired/revoked',
  `expire_at`     DATETIME(6)  NOT NULL COMMENT '过期时间',
  `refreshed_at`  DATETIME(6)  DEFAULT NULL COMMENT '刷新时间',
  `created_at`    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bind_code` (`code`),
  KEY `idx_bind_workspace_status` (`workspace_id`, `status`),
  CONSTRAINT `fk_bind_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `t_workspace` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备绑定码';

CREATE TABLE `t_device_issue_report` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id`    BIGINT       NOT NULL COMMENT '设备ID',
  `description`  VARCHAR(1000) NOT NULL COMMENT '问题描述',
  `tracking_no`  VARCHAR(64)  DEFAULT NULL COMMENT '跟踪号',
  `status`       VARCHAR(32)  NOT NULL DEFAULT 'submitted' COMMENT 'submitted/processing/closed',
  `created_by`   BIGINT       DEFAULT NULL COMMENT '创建人用户ID',
  `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_issue_device` (`device_id`),
  KEY `idx_issue_tracking` (`tracking_no`),
  CONSTRAINT `fk_issue_device` FOREIGN KEY (`device_id`) REFERENCES `t_device` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备定损反馈';

-- =============================================================================
-- 3. Map
-- =============================================================================
CREATE TABLE `t_annotation_folder` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` BIGINT       NOT NULL DEFAULT 1,
  `name`         VARCHAR(128) NOT NULL COMMENT '文件夹名',
  `locked`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否锁定',
  `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ann_folder_ws` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标注文件夹';

CREATE TABLE `t_annotation` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` BIGINT       NOT NULL DEFAULT 1,
  `folder_id`    BIGINT       DEFAULT NULL COMMENT '文件夹ID',
  `name`         VARCHAR(128) NOT NULL COMMENT '名称',
  `geom_type`    VARCHAR(16)  NOT NULL COMMENT 'point/line/polygon',
  `purpose`      VARCHAR(32)  DEFAULT NULL COMMENT 'work_area/no_fly/poi/other',
  `geometry`     JSON         NOT NULL COMMENT 'WGS84 GeoJSON',
  `style`        JSON         DEFAULT NULL COMMENT '样式',
  `visible`      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示',
  `sync_status`  VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT 'pending/synced/failed',
  `updated_by`   BIGINT       DEFAULT NULL COMMENT '更新人',
  `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_annotation_folder` (`folder_id`),
  KEY `idx_annotation_purpose` (`workspace_id`, `purpose`),
  CONSTRAINT `fk_annotation_folder` FOREIGN KEY (`folder_id`) REFERENCES `t_annotation_folder` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地图标注';

CREATE TABLE `t_sync_job` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` BIGINT       NOT NULL DEFAULT 1,
  `status`       VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed',
  `message`      VARCHAR(500) DEFAULT NULL COMMENT '结果消息',
  `started_at`   DATETIME(6)  DEFAULT NULL,
  `finished_at`  DATETIME(6)  DEFAULT NULL,
  `created_by`   BIGINT       DEFAULT NULL,
  `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_sync_job_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标注同步任务';

CREATE TABLE `t_sync_job_item` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sync_job_id`   BIGINT      NOT NULL COMMENT '同步任务ID',
  `annotation_id` BIGINT      NOT NULL COMMENT '标注ID',
  `device_id`     BIGINT      NOT NULL COMMENT '设备ID',
  `result`        VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'pending/success/failed',
  `error_message` VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sji_job` (`sync_job_id`),
  KEY `idx_sji_device` (`device_id`),
  CONSTRAINT `fk_sji_job` FOREIGN KEY (`sync_job_id`) REFERENCES `t_sync_job` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_sji_annotation` FOREIGN KEY (`annotation_id`) REFERENCES `t_annotation` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_sji_device` FOREIGN KEY (`device_id`) REFERENCES `t_device` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标注同步明细';

-- =============================================================================
-- 3.1 Safety / 空域限飞
-- =============================================================================
CREATE TABLE `t_safety_area` (
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
  `fh2_flight_area_id` VARCHAR(64)  DEFAULT NULL COMMENT '司空飞行安全区 UUID',
  `created_by`       BIGINT         DEFAULT NULL,
  `updated_by`       BIGINT         DEFAULT NULL,
  `created_at`       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`          TINYINT        NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_safety_area_ws` (`workspace_id`, `deleted`),
  KEY `idx_safety_fh2_area` (`fh2_flight_area_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空域限飞区';

CREATE TABLE `t_safety_device_sync` (
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

-- =============================================================================
-- 4. Wayline
-- =============================================================================
CREATE TABLE `t_wayline_folder` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` BIGINT       NOT NULL DEFAULT 1,
  `name`         VARCHAR(128) NOT NULL COMMENT '文件夹名',
  `parent_id`    BIGINT       DEFAULT NULL COMMENT '父文件夹',
  `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_wayline_folder_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='航线文件夹';

CREATE TABLE `t_wayline` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`   BIGINT       NOT NULL DEFAULT 1,
  `folder_id`      BIGINT       DEFAULT NULL,
  `name`           VARCHAR(128) NOT NULL COMMENT '航线名称',
  `aircraft_model` VARCHAR(64)  NOT NULL COMMENT '适用机型',
  `wayline_type`   VARCHAR(32)  NOT NULL COMMENT 'waypoint/grid_2d/slope/geometry_fine',
  `status`         VARCHAR(32)  NOT NULL DEFAULT 'draft' COMMENT 'draft/published',
  `updated_by`     BIGINT       DEFAULT NULL,
  `created_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_wayline_folder` (`folder_id`),
  KEY `idx_wayline_model_time` (`aircraft_model`, `updated_at`),
  KEY `idx_wayline_ws_status` (`workspace_id`, `status`),
  CONSTRAINT `fk_wayline_folder` FOREIGN KEY (`folder_id`) REFERENCES `t_wayline_folder` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='航线';

CREATE TABLE `t_wayline_content` (
  `wayline_id`         BIGINT       NOT NULL COMMENT '航线ID',
  `storage_key`        VARCHAR(512) DEFAULT NULL COMMENT 'OSS KMZ 路径',
  `structured_params`  JSON         DEFAULT NULL COMMENT '编辑器结构化参数',
  `checksum`           VARCHAR(64)  DEFAULT NULL COMMENT '校验和',
  `updated_at`         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`wayline_id`),
  CONSTRAINT `fk_wayline_content` FOREIGN KEY (`wayline_id`) REFERENCES `t_wayline` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='航线内容';

CREATE TABLE `t_wayline_snapshot` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `source_wayline_id`  BIGINT       DEFAULT NULL COMMENT '来源航线ID可空',
  `name`               VARCHAR(128) NOT NULL COMMENT '快照名称',
  `aircraft_model`     VARCHAR(64)  NOT NULL,
  `wayline_type`       VARCHAR(32)  NOT NULL,
  `storage_key`        VARCHAR(512) DEFAULT NULL,
  `structured_params`  JSON         DEFAULT NULL,
  `checksum`           VARCHAR(64)  DEFAULT NULL,
  `created_at`         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_ws_source` (`source_wayline_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='航线快照(任务引用)';

-- =============================================================================
-- 5. Mission
-- =============================================================================
CREATE TABLE `t_mission_plan` (
  `id`                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`         BIGINT        NOT NULL DEFAULT 1,
  `name`                 VARCHAR(128)  NOT NULL COMMENT '计划名称',
  `task_type`            VARCHAR(32)   NOT NULL COMMENT 'normal/wayline/leapfrog/manual',
  `wayline_id`           BIGINT        DEFAULT NULL COMMENT '源航线ID',
  `wayline_snapshot_id`  BIGINT        DEFAULT NULL COMMENT '航线快照ID',
  `device_id`            BIGINT        NOT NULL COMMENT '执行设备ID',
  `landing_device_id`    BIGINT        DEFAULT NULL COMMENT '蛙跳任务降落机场ID',
  `strategy`             VARCHAR(32)   NOT NULL COMMENT 'immediate/once/recurring/continuous',
  `schedule_at`          DATETIME(6)   DEFAULT NULL COMMENT '定时时间(单次)',
  `cron_expr`            VARCHAR(64)   DEFAULT NULL COMMENT '重复频率摘要如 1,day',
  `schedule_config`      TEXT          DEFAULT NULL COMMENT '策略调度明细JSON',
  `accuracy_mode`        VARCHAR(16)   NOT NULL DEFAULT 'rtk' COMMENT 'rtk/gnss',
  `breakpoint_resume`    TINYINT       NOT NULL DEFAULT 1 COMMENT '断点续飞',
  `smart_rth`            TINYINT       NOT NULL DEFAULT 1 COMMENT '智能返航规划',
  `min_flight_height_m`  DECIMAL(10,2) NOT NULL DEFAULT 60.00 COMMENT '最低飞行高度m',
  `signal_lost_action`   VARCHAR(16)   NOT NULL DEFAULT 'rth' COMMENT 'rth/continue',
  `multimodal_enabled`   TINYINT       NOT NULL DEFAULT 0 COMMENT '多模态识别',
  `status`               VARCHAR(32)   NOT NULL DEFAULT 'pending' COMMENT '见状态字典',
  `created_by`           BIGINT        DEFAULT NULL,
  `created_at`           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`              TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_mission_status_schedule` (`status`, `schedule_at`),
  KEY `idx_mission_device` (`device_id`),
  KEY `idx_mission_landing_device` (`landing_device_id`),
  KEY `idx_mission_wayline` (`wayline_id`),
  CONSTRAINT `fk_mission_device` FOREIGN KEY (`device_id`) REFERENCES `t_device` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_mission_landing_device` FOREIGN KEY (`landing_device_id`) REFERENCES `t_device` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_mission_wayline` FOREIGN KEY (`wayline_id`) REFERENCES `t_wayline` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_mission_snapshot` FOREIGN KEY (`wayline_snapshot_id`) REFERENCES `t_wayline_snapshot` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务计划';

CREATE TABLE `t_mission_execution` (
  `id`                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plan_id`              BIGINT         NOT NULL COMMENT '计划ID',
  `status`               VARCHAR(32)    NOT NULL COMMENT '执行状态',
  `planned_at`           DATETIME(6)    DEFAULT NULL COMMENT '计划执行时间',
  `actual_start_at`      DATETIME(6)    DEFAULT NULL,
  `actual_end_at`        DATETIME(6)    DEFAULT NULL,
  `flight_duration_sec`  DECIMAL(14,2)  DEFAULT NULL COMMENT '飞行时长秒',
  `flight_distance_m`    DECIMAL(14,2)  DEFAULT NULL COMMENT '飞行里程米',
  `fail_reason`          VARCHAR(500)   DEFAULT NULL,
  `breakpoint_info`      JSON           DEFAULT NULL COMMENT '断点信息',
  `fh2_task_uuid`        VARCHAR(64)    DEFAULT NULL COMMENT '司空飞行任务 UUID',
  `created_at`           DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`           DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`              TINYINT        NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_exec_plan` (`plan_id`),
  KEY `idx_exec_status_time` (`status`, `actual_start_at`),
  KEY `idx_exec_fh2_task` (`fh2_task_uuid`),
  CONSTRAINT `fk_exec_plan` FOREIGN KEY (`plan_id`) REFERENCES `t_mission_plan` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务执行记录';

-- =============================================================================
-- 6. Media
-- =============================================================================
CREATE TABLE `t_media_folder` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` BIGINT       NOT NULL DEFAULT 1,
  `name`         VARCHAR(128) NOT NULL,
  `category`     VARCHAR(32)  NOT NULL DEFAULT 'media' COMMENT 'media/model/design/trash',
  `parent_id`    BIGINT       DEFAULT NULL,
  `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_media_folder_cat` (`workspace_id`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体文件夹';

CREATE TABLE `t_media_asset` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`          BIGINT       NOT NULL DEFAULT 1,
  `folder_id`             BIGINT       DEFAULT NULL,
  `name`                  VARCHAR(255) NOT NULL,
  `media_type`            VARCHAR(32)  NOT NULL COMMENT 'photo/video/archive',
  `payload`               VARCHAR(64)  DEFAULT NULL COMMENT '拍摄负载',
  `size_bytes`            BIGINT       NOT NULL DEFAULT 0,
  `storage_key`           VARCHAR(512) NOT NULL COMMENT 'OSS Key',
  `source`                VARCHAR(32)  NOT NULL DEFAULT 'upload' COMMENT 'upload/livestream_record/mission_return/import',
  `mission_execution_id`  BIGINT       DEFAULT NULL,
  `recycle_status`        VARCHAR(16)  NOT NULL DEFAULT 'normal' COMMENT 'normal/in_trash/deleted',
  `created_at`            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`               TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_media_folder` (`folder_id`),
  KEY `idx_media_type_time` (`media_type`, `created_at`),
  KEY `idx_media_exec` (`mission_execution_id`),
  KEY `idx_media_recycle` (`workspace_id`, `recycle_status`),
  CONSTRAINT `fk_media_folder` FOREIGN KEY (`folder_id`) REFERENCES `t_media_folder` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_media_exec` FOREIGN KEY (`mission_execution_id`) REFERENCES `t_mission_execution` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体资源';

CREATE TABLE `t_media_tag` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `media_id`   BIGINT      NOT NULL COMMENT '媒体ID',
  `tag`        VARCHAR(64) NOT NULL COMMENT '标签',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_media_tag` (`media_id`, `tag`),
  KEY `idx_tag` (`tag`),
  CONSTRAINT `fk_media_tag_media` FOREIGN KEY (`media_id`) REFERENCES `t_media_asset` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体标签';

CREATE TABLE `t_model_asset` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`   BIGINT       NOT NULL DEFAULT 1,
  `name`           VARCHAR(255) NOT NULL,
  `model_type`     VARCHAR(32)  NOT NULL COMMENT 'mesh_2d/point_cloud/mesh_3d',
  `storage_key`    VARCHAR(512) NOT NULL,
  `size_bytes`     BIGINT       NOT NULL DEFAULT 0,
  `recycle_status` VARCHAR(16)  NOT NULL DEFAULT 'normal',
  `created_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_model_ws_type` (`workspace_id`, `model_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型资源';

CREATE TABLE `t_reconstruct_job` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`  BIGINT       NOT NULL DEFAULT 1,
  `status`        VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed',
  `model_id`      BIGINT       DEFAULT NULL COMMENT '产出模型ID',
  `error_message` VARCHAR(500) DEFAULT NULL,
  `created_by`    BIGINT       DEFAULT NULL,
  `created_at`    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `finished_at`   DATETIME(6)  DEFAULT NULL,
  `updated_at`    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_recon_status` (`status`),
  CONSTRAINT `fk_recon_model` FOREIGN KEY (`model_id`) REFERENCES `t_model_asset` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型重建任务';

CREATE TABLE `t_reconstruct_job_media` (
  `job_id`   BIGINT NOT NULL,
  `media_id` BIGINT NOT NULL,
  PRIMARY KEY (`job_id`, `media_id`),
  KEY `idx_rjm_media` (`media_id`),
  CONSTRAINT `fk_rjm_job` FOREIGN KEY (`job_id`) REFERENCES `t_reconstruct_job` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_rjm_media` FOREIGN KEY (`media_id`) REFERENCES `t_media_asset` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='重建任务-媒体关联';

CREATE TABLE `t_workspace_quota` (
  `id`                         BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`               BIGINT NOT NULL COMMENT '作业空间ID',
  `storage_total_bytes`        BIGINT NOT NULL DEFAULT 0 COMMENT '存储总额',
  `storage_used_bytes`         BIGINT NOT NULL DEFAULT 0 COMMENT '已用存储',
  `mapping_quota`              INT    NOT NULL DEFAULT 0 COMMENT '建图配额张数',
  `mapping_used`               INT    NOT NULL DEFAULT 0 COMMENT '已用建图',
  `livestream_minutes_total`   INT    NOT NULL DEFAULT 0 COMMENT '直播分钟配额',
  `livestream_minutes_used`    INT    NOT NULL DEFAULT 0 COMMENT '已用直播分钟',
  `updated_at`                 DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quota_workspace` (`workspace_id`),
  CONSTRAINT `fk_quota_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `t_workspace` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业空间配额';

-- =============================================================================
-- 7. Analytics（读模型）
-- =============================================================================
CREATE TABLE `t_dashboard_snapshot` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id`          BIGINT        NOT NULL DEFAULT 1,
  `computed_at`           DATETIME(6)   NOT NULL COMMENT '计算时间',
  `aircraft_count`        INT           NOT NULL DEFAULT 0,
  `dock_idle`             INT           NOT NULL DEFAULT 0,
  `dock_working`          INT           NOT NULL DEFAULT 0,
  `dock_debugging`        INT           NOT NULL DEFAULT 0,
  `dock_offline`          INT           NOT NULL DEFAULT 0,
  `member_count`          INT           NOT NULL DEFAULT 0,
  `wayline_count`         INT           NOT NULL DEFAULT 0,
  `algorithm_count`       INT           NOT NULL DEFAULT 0,
  `media_count`           INT           NOT NULL DEFAULT 0,
  `model_count`           INT           NOT NULL DEFAULT 0,
  `report_count`          INT           NOT NULL DEFAULT 0,
  `ai_alarm_count`        INT           NOT NULL DEFAULT 0,
  `workflow_count`        INT           NOT NULL DEFAULT 0,
  `workflow_cycles`       INT           NOT NULL DEFAULT 0,
  `workflow_success_rate` DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
  PRIMARY KEY (`id`),
  KEY `idx_dash_ws_time` (`workspace_id`, `computed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='看板快照';

CREATE TABLE `t_flight_stat_daily` (
  `workspace_id`  BIGINT        NOT NULL DEFAULT 1,
  `stat_date`     DATE          NOT NULL COMMENT '统计日',
  `sortie_count`  INT           NOT NULL DEFAULT 0 COMMENT '架次',
  `duration_sec`  DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '时长秒',
  `distance_m`    DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '里程米',
  `updated_at`    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`workspace_id`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='飞行日统计';

CREATE TABLE `t_mission_stat_daily` (
  `workspace_id`   BIGINT      NOT NULL DEFAULT 1,
  `stat_date`      DATE        NOT NULL,
  `planned_count`  INT         NOT NULL DEFAULT 0,
  `manual_count`   INT         NOT NULL DEFAULT 0,
  `success_count`  INT         NOT NULL DEFAULT 0,
  `paused_count`   INT         NOT NULL DEFAULT 0,
  `failed_count`   INT         NOT NULL DEFAULT 0,
  `updated_at`     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`workspace_id`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务日统计';

-- =============================================================================
-- 8. Algorithm（P2）
-- =============================================================================
CREATE TABLE `t_algorithm` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` BIGINT       NOT NULL DEFAULT 1,
  `name`         VARCHAR(128) NOT NULL,
  `algo_type`    VARCHAR(32)  NOT NULL COMMENT 'detection/multimodal/...',
  `status`       VARCHAR(32)  NOT NULL DEFAULT 'disabled' COMMENT 'enabled/disabled/not_opened',
  `config`       JSON         DEFAULT NULL,
  `created_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_algo_ws_status` (`workspace_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='算法';

CREATE TABLE `t_algorithm_alarm` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `algorithm_id`  BIGINT       NOT NULL,
  `target_label`  VARCHAR(128) DEFAULT NULL COMMENT '目标标签',
  `source`        VARCHAR(64)  DEFAULT NULL COMMENT '来源 livestream/replay',
  `payload`       JSON         DEFAULT NULL,
  `raised_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `created_at`    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_alarm_algo_time` (`algorithm_id`, `raised_at`),
  CONSTRAINT `fk_alarm_algo` FOREIGN KEY (`algorithm_id`) REFERENCES `t_algorithm` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='算法告警';

-- =============================================================================
-- 9. 初始化数据
-- =============================================================================
INSERT INTO `t_workspace` (`id`, `workspace_code`, `name`, `status`)
VALUES (1, 'aiflow-workspace-001', 'AiFlow 默认作业空间', 'active');

INSERT INTO `t_workspace_quota` (
  `workspace_id`, `storage_total_bytes`, `storage_used_bytes`,
  `mapping_quota`, `mapping_used`, `livestream_minutes_total`, `livestream_minutes_used`
) VALUES (
  1, 5368709120, 0, 1000, 0, 10000, 0
);

INSERT INTO `t_role` (`id`, `code`, `name`, `description`, `is_system`, `locked`) VALUES
(1, 'super_admin', '超级管理员', '全部权限', 1, 1),
(2, 'admin', '管理员', '业务配置与任务', 1, 0),
(3, 'maintainer', '设备维护员', '设备运维为主', 1, 0),
(4, 'operator', '作业员', '执行与查看', 1, 0);

INSERT INTO `t_permission` (`code`, `module`, `action`, `name`) VALUES
('dashboard:read', 'dashboard', 'read', '看板查看'),
('map:read', 'map', 'read', '地图查看'),
('map:write', 'map', 'write', '地图编辑'),
('wayline:read', 'wayline', 'read', '航线查看'),
('wayline:write', 'wayline', 'write', '航线编辑'),
('mission:read', 'mission', 'read', '任务查看'),
('mission:write', 'mission', 'write', '任务编辑'),
('library:read', 'library', 'read', '资源库查看'),
('library:write', 'library', 'write', '资源库编辑'),
('member:read', 'member', 'read', '人员查看'),
('member:write', 'member', 'write', '人员编辑'),
('device:read', 'device', 'read', '设备查看'),
('device:write', 'device', 'write', '设备编辑'),
('algorithm:read', 'algorithm', 'read', '算法查看'),
('algorithm:write', 'algorithm', 'write', '算法编辑'),
('role:manage', 'role', 'manage', '角色管理');

-- 超管：全部权限
INSERT INTO `t_role_permission` (`role_id`, `permission_id`)
SELECT 1, id FROM `t_permission` WHERE `deleted` = 0;

-- 管理员
INSERT INTO `t_role_permission` (`role_id`, `permission_id`)
SELECT 2, id FROM `t_permission`
WHERE `code` IN (
  'dashboard:read','map:read','map:write','wayline:read','wayline:write',
  'mission:read','mission:write','library:read','library:write',
  'device:read','device:write','algorithm:read','algorithm:write','member:read'
);

-- 设备维护员
INSERT INTO `t_role_permission` (`role_id`, `permission_id`)
SELECT 3, id FROM `t_permission`
WHERE `code` IN ('dashboard:read','device:read','device:write','library:read');

-- 作业员
INSERT INTO `t_role_permission` (`role_id`, `permission_id`)
SELECT 4, id FROM `t_permission`
WHERE `code` IN (
  'dashboard:read','map:read','map:write','wayline:read','wayline:write',
  'mission:read','mission:write','library:read','library:write'
);

-- 默认超管账号（密码占位：请在应用层替换为真实 BCrypt；此处为 bcrypt('123456') 示例哈希需上线前更换）
INSERT INTO `t_user` (
  `workspace_id`, `account`, `display_name`, `password_hash`, `status`, `join_method`, `role_id`
) VALUES (
  1, 'admin@aiflow.local', '平台超管',
  '$2b$10$ZZvI7iicUayuBQoO8OipYugInj1Fk5H03wl5iOU7r45z9E87dpiDy',
  'active', 'organization_add', 1
);

INSERT INTO `t_bind_code` (`workspace_id`, `code`, `status`, `expire_at`, `refreshed_at`)
VALUES (1, 'BIND-8F3K-92QX', 'active', DATE_ADD(NOW(6), INTERVAL 7 DAY), NOW(6));
