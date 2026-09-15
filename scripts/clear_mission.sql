TRUNCATE TABLE t_mission_execution;
TRUNCATE TABLE t_mission_plan;
SELECT 't_mission_plan' AS tbl, COUNT(*) AS cnt_after FROM t_mission_plan
UNION ALL
SELECT 't_mission_execution', COUNT(*) FROM t_mission_execution;
