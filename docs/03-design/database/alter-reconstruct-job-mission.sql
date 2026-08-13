-- 任务三维重建：重建任务绑定飞行计划执行 + 司空模型引用
-- 执行库：UAVPlatform

ALTER TABLE `t_reconstruct_job`
  ADD COLUMN `mission_plan_id` BIGINT DEFAULT NULL COMMENT '飞行计划ID' AFTER `workspace_id`,
  ADD COLUMN `mission_execution_id` BIGINT DEFAULT NULL COMMENT '执行记录ID' AFTER `mission_plan_id`,
  ADD COLUMN `fh2_task_uuid` VARCHAR(64) DEFAULT NULL COMMENT '司空飞行任务UUID' AFTER `mission_execution_id`,
  ADD COLUMN `fh2_model_id` BIGINT DEFAULT NULL COMMENT '司空模型ID' AFTER `fh2_task_uuid`,
  ADD COLUMN `fh2_task_folder_id` BIGINT DEFAULT NULL COMMENT '司空任务资源文件夹ID' AFTER `fh2_model_id`,
  ADD COLUMN `preview_url` VARCHAR(1024) DEFAULT NULL COMMENT '司空模型预览图' AFTER `error_message`,
  ADD COLUMN `model_url` VARCHAR(1024) DEFAULT NULL COMMENT '司空模型资源/下载地址' AFTER `preview_url`,
  ADD KEY `idx_recon_exec` (`mission_execution_id`),
  ADD KEY `idx_recon_ws_status` (`workspace_id`, `status`);
