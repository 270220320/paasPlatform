-- 纠正：从未绑定 FH2 飞行任务却被标成「执行中」的计划/执行记录
-- 背景：旧逻辑在创建立即任务时直接写 running + actual_start_at，与是否起飞无关

UPDATE t_mission_execution
SET status = 'pending',
    actual_start_at = NULL
WHERE status = 'running'
  AND (fh2_task_uuid IS NULL OR TRIM(fh2_task_uuid) = '');

UPDATE t_mission_plan p
SET p.status = 'pending'
WHERE p.status = 'running'
  AND NOT EXISTS (
    SELECT 1
    FROM t_mission_execution e
    WHERE e.plan_id = p.id
      AND e.fh2_task_uuid IS NOT NULL
      AND TRIM(e.fh2_task_uuid) <> ''
  );
