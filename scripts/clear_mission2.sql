SELECT 't_media_asset_before' AS tbl, COUNT(*) AS cnt FROM t_media_asset;
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE t_media_asset;
TRUNCATE TABLE t_mission_execution;
TRUNCATE TABLE t_mission_plan;
SET FOREIGN_KEY_CHECKS = 1;
SELECT 't_mission_plan' AS tbl, COUNT(*) AS cnt_after FROM t_mission_plan
UNION ALL SELECT 't_mission_execution', COUNT(*) FROM t_mission_execution
UNION ALL SELECT 't_media_asset', COUNT(*) FROM t_media_asset;
