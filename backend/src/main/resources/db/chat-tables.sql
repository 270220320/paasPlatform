-- 对话控制模块（chat）建表脚本
-- 执行：mysql -uroot -p UAVPlatform < chat-tables.sql

CREATE TABLE IF NOT EXISTS t_chat_session (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  workspace_id BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  title       VARCHAR(128) NOT NULL DEFAULT '新对话',
  dock_sn     VARCHAR(64)  NULL COMMENT '当前选定机场 SN',
  drone_sn    VARCHAR(64)  NULL COMMENT '当前选定无人机 SN',
  status      VARCHAR(16)  NOT NULL DEFAULT 'active',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT  NOT NULL DEFAULT 0,
  KEY idx_ws_user (workspace_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话控制会话';

CREATE TABLE IF NOT EXISTS t_chat_message (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id  BIGINT NOT NULL,
  role        VARCHAR(16) NOT NULL COMMENT 'user/assistant/tool',
  content     TEXT NULL COMMENT '文本内容（user/assistant）',
  thinking    TEXT NULL COMMENT '模型思考内容（assistant）',
  tool_name   VARCHAR(64) NULL COMMENT '工具名（tool）',
  tool_args   TEXT NULL COMMENT '工具入参 JSON',
  tool_result TEXT NULL COMMENT '工具结果 JSON',
  status      VARCHAR(16) NULL COMMENT 'tool: ok/pending/confirmed/cancelled/error',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话控制消息';
