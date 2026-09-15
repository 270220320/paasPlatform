-- 站内通知模块（notification）建表脚本
-- 执行：mysql -uroot -p UAVPlatform < notification-tables.sql

CREATE TABLE IF NOT EXISTS t_notification (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  workspace_id BIGINT NOT NULL,
  user_id      BIGINT NOT NULL COMMENT '接收人（工作区成员各存一份副本）',
  type         VARCHAR(32) NOT NULL COMMENT 'mission.takeoff/mission.completed/mission.failed/mission.dispatch_failed/device.offline/reconstruct.success/reconstruct.failed/wayline.imported/wayline.import_failed',
  level        VARCHAR(16) NOT NULL DEFAULT 'info' COMMENT 'info/success/warning/error',
  title        VARCHAR(128) NOT NULL,
  content      VARCHAR(512) NULL,
  ref_type     VARCHAR(32) NULL COMMENT '关联对象：mission_plan/reconstruct_job/wayline/device',
  ref_id       BIGINT NULL,
  read_at      DATETIME NULL COMMENT 'NULL=未读',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted      TINYINT  NOT NULL DEFAULT 0,
  KEY idx_ws_user_read (workspace_id, user_id, read_at),
  KEY idx_ws_user_id (workspace_id, user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';
