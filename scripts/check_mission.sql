SELECT 't_mission_plan_active' AS tbl, COUNT(*) AS cnt FROM t_mission_plan WHERE deleted = 0
UNION ALL
SELECT 't_mission_plan_all', COUNT(*) FROM t_mission_plan
UNION ALL
SELECT 't_mission_execution_all', COUNT(*) FROM t_mission_execution;
SELECT id, name, task_type, status, created_at FROM t_mission_plan WHERE deleted = 0 ORDER BY id;
