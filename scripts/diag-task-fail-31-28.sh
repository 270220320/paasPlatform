#!/usr/bin/env bash
# 219015 排查（十三）：es-task config 目录 + PG tasks 表实际数据 + tas/ts-scheduler 事发时间窗
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===ES_TASK_CONFIG_LS===';
SE docker exec es-task sh -c 'ls -la /app/config; for f in /app/config/*; do echo "--- $f"; head -c 300 "$f" | tr -d "\0"; echo; done' | head -80;
echo '===CONFIG_GREP_TASKTYPE===';
SE docker exec es-task sh -c 'grep -ri "task type" /app/config 2>/dev/null; grep -ri "unsupported" /app/config 2>/dev/null' | head -20;
echo '===PG_DBS===';
SE docker exec postgres_instance_1 psql -U app_rw -d postgres -tAc "SELECT datname FROM pg_database" | head -25;
echo '===PG_FIND_TASKS_TABLE===';
SE docker exec postgres_instance_1 psql -U app_rw -d postgres -tAc "SELECT table_schema||'.'||table_name FROM information_schema.tables WHERE table_name IN ('tasks','task_waylines','flight_tasks')" | head -10;
echo '===TAS_WINDOW===';
SE docker logs tas-service --since 24h 2>&1 | grep -a '09:28:5' | head -25 | cut -c1-700;
echo '===TS_SCHEDULER_WINDOW===';
SE docker logs ts-scheduler --since 24h 2>&1 | grep -a '09:28:5' | head -15 | cut -c1-700;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
