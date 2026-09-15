#!/usr/bin/env bash
# 3.1升级后飞行任务失败排查（七）：flight_tasks表结构+行数据
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===COLS===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 psql -U app_rw -d es_task -c "SELECT column_name, data_type FROM information_schema.columns WHERE table_name='flight_tasks' ORDER BY ordinal_position" 2>&1 | head -40;
echo '===ROW===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 psql -U app_rw -d es_task -x -c "SELECT * FROM flight_tasks ORDER BY created_at DESC LIMIT 1" 2>&1 | head -60;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
