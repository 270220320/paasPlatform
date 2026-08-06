-- 限飞区关联司空飞行安全区 ID
ALTER TABLE `t_safety_area`
  ADD COLUMN `fh2_flight_area_id` VARCHAR(64) DEFAULT NULL COMMENT '司空飞行安全区 UUID' AFTER `sync_status`;

ALTER TABLE `t_safety_area`
  ADD KEY `idx_safety_fh2_area` (`fh2_flight_area_id`);
