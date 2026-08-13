-- 司空模型签名下载地址常超过 VARCHAR(1024)，改为 TEXT
-- 执行库：UAVPlatform

ALTER TABLE `t_reconstruct_job`
  MODIFY COLUMN `preview_url` TEXT NULL COMMENT '司空模型预览图',
  MODIFY COLUMN `model_url` TEXT NULL COMMENT '司空模型资源/下载地址';
