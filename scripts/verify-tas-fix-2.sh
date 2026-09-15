#!/usr/bin/env bash
# 修复验证（八）：刚失败任务的链路取证（AiFlow 错误 + es-task ready 处理 + 任务状态）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===ES_TASK_LAST_15MIN===';
SE docker logs es-task --since 15m 2>&1 | grep -aiE 'error|warn|unsupported|ready|flighttask|update.*status|status.*update|start_failed|execute' | grep -av 'es-duplex' | tail -25 | cut -c1-600;
echo '===TASK_TYPE_ERR_COUNT===';
SE docker logs es-task --since 15m 2>&1 | grep -ac 'task type unsupported';
echo '===PUT_STATUS_RESP===';
SE docker logs es-task --since 15m 2>&1 | grep -a 'flight-tasks' | grep -aiE 'status|PUT' | tail -6 | cut -c1-700;
echo '===TAS_EXECUTE_MSG===';
SE docker logs tas-service --since 15m 2>&1 | grep -a 'flighttask' | tail -10 | cut -c1-450;
echo '===TASKS_TABLE_LATEST===';
P=$(S docker exec postgres_instance_1 printenv POSTGRES_USER);
S docker exec postgres_instance_1 psql -U ${P:-app_rw} -d es_task -x -c "SELECT flight_task_id,task_type,task_status,flight_task_status,task_name,begin_at,end_at,updated_at FROM flight_tasks ORDER BY updated_at DESC LIMIT 1" 2>&1 | head -20;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
