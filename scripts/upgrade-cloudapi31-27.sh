#!/usr/bin/env bash
# 司空3.1升级（二十七）：全表扫描定位"飞行计划"12条记录的真实存储表
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===ES_TASK_TABLES_ROWCOUNT===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 psql -U app_rw -d es_task -Atc "SELECT relname, n_live_tup FROM pg_stat_user_tables ORDER BY n_live_tup DESC, relname" 2>&1 | head -40;
echo '===ES_TASK_SCHEDULER===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 psql -U app_rw -d es_task_scheduler -Atc "SELECT relname, n_live_tup FROM pg_stat_user_tables WHERE n_live_tup > 0 ORDER BY n_live_tup DESC" 2>&1 | head -15;
echo '===PF_CONTROL_CORE===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 psql -U app_rw -d pf_control_core -Atc "SELECT relname, n_live_tup FROM pg_stat_user_tables WHERE n_live_tup > 0 ORDER BY n_live_tup DESC" 2>&1 | head -10;
echo '===MESSAGE_DB===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 psql -U app_rw -d message_db -Atc "SELECT relname, n_live_tup FROM pg_stat_user_tables WHERE n_live_tup > 0 ORDER BY n_live_tup DESC" 2>&1 | head -10;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
