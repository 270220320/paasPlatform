SHOW COLUMNS FROM t_mission_execution;
SELECT * FROM t_mission_execution ORDER BY id DESC LIMIT 1\G
SELECT COUNT(*) AS wayline_cnt FROM t_wayline;
SELECT id, name, fh2_wayline_uuid FROM t_wayline LIMIT 10;
