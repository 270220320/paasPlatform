-- 蛙跳任务：记录降落机场。执行时 SDK 将以 device_id 为起飞机场、landing_device_id 为降落机场。
ALTER TABLE `t_mission_plan`
  ADD COLUMN `landing_device_id` BIGINT DEFAULT NULL COMMENT '蛙跳任务降落机场ID' AFTER `device_id`,
  ADD KEY `idx_mission_landing_device` (`landing_device_id`),
  ADD CONSTRAINT `fk_mission_landing_device`
    FOREIGN KEY (`landing_device_id`) REFERENCES `t_device` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
