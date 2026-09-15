#!/usr/bin/env bash
# 219015 排查：司空DB任务现状态 + tas日志 + 完整反编译 execute/control
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===DB_TASK_NOW===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 psql -U app_rw -d es_task -x -c "SELECT flight_task_id, task_type, task_status, flight_task_status, begin_at, end_at, run_at, completed_at, created_at FROM flight_tasks ORDER BY id DESC LIMIT 2" 2>&1 | head -40;
echo '===TAS_219015===' ;
echo 'Tksc@197653' | sudo -S docker logs tas-service --since 3h 2>&1 | grep -aiE "219015|status.*update|flighttask_execute" | tail -12;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
