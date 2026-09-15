#!/usr/bin/env bash
# 3.1升级后飞行任务失败排查（六）：flight_tasks任务详情
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===TASK_ROW===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 psql -U app_rw -d es_task -x -c "SELECT * FROM flight_tasks WHERE uuid='3e384fa2-31fe-47d5-884d-a13ac635d7a6'" 2>&1 | head -80;
echo '===TAS_EXECUTE_ANY===';
echo 'Tksc@197653' | sudo -S docker logs tas-service --since 12h 2>&1 | grep -a "flighttask_execute" | tail -6;
echo '===TAS_READY_AFTER===';
echo 'Tksc@197653' | sudo -S docker logs tas-service --since 12h 2>&1 | grep -aE "08:5[6-9].*(execute|ready|3e384fa2|8a4fe6dd)" | tail -15;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
