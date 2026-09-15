#!/usr/bin/env bash
# 司空3.1升级（二十八）：查看 es_task_scheduler.task 表结构与29条内容
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===TASK_TABLE_COLS===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 psql -U app_rw -d es_task_scheduler -Atc "SELECT column_name FROM information_schema.columns WHERE table_name='task'" 2>&1 | tr '\n' ' ';
echo;
echo '===TASK_ROWS_OVERVIEW===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 psql -U app_rw -d es_task_scheduler -c "SELECT id, name, type, status, enable, created_at FROM task ORDER BY id LIMIT 35" 2>&1 | head -45;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
