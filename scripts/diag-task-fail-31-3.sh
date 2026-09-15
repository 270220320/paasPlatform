#!/usr/bin/env bash
# 3.1升级后飞行任务失败排查（三）：司空DB任务详情 + tas execute调度日志
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===ES_TASK_TABLES===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance psql -U app_rw -d es_task -c "\dt" 2>&1 | grep -v '^$' | head -25;
echo '===TASK_ROW===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance psql -U app_rw -d es_task -x -c "SELECT * FROM pf_flight_task WHERE uuid='3e384fa2-31fe-47d5-884d-a13ac635d7a6'" 2>&1 | head -60;
echo '===TAS_EXECUTE_LOG===';
echo 'Tksc@197653' | sudo -S docker logs tas-service --since 12h 2>&1 | grep -a "flighttask_execute" | tail -8;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
