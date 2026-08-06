-- 执行记录关联司空飞行任务 UUID（FlyDataReplay.taskId）
ALTER TABLE `t_mission_execution`
  ADD COLUMN `fh2_task_uuid` VARCHAR(64) DEFAULT NULL COMMENT '司空飞行任务 UUID' AFTER `breakpoint_info`;

ALTER TABLE `t_mission_execution`
  ADD KEY `idx_exec_fh2_task` (`fh2_task_uuid`);
