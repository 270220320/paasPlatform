-- 任务计划：按策略存储执行日期/时段/频率/电量等明细
ALTER TABLE `t_mission_plan`
  ADD COLUMN `schedule_config` TEXT DEFAULT NULL COMMENT '策略调度明细JSON' AFTER `cron_expr`;
