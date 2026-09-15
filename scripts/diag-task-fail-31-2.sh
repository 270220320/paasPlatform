#!/usr/bin/env bash
# 3.1升级后飞行任务失败排查（二）：司空侧任务日志 + 航线库现状 + 本地计划关联
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===WAYLINE_FILES_NOW===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 psql -U app_rw -d file_storage -c "SELECT id, task_name, kmz_without_res, created_at FROM wayline_files ORDER BY id DESC LIMIT 5" 2>&1 | head -12;
echo '===TAS_LOG_TASK===';
echo 'Tksc@197653' | sudo -S docker logs tas-service --since 10h 2>&1 | grep -a "3e384fa2" | tail -20;
echo '===TAS_LOG_ERR===';
echo 'Tksc@197653' | sudo -S docker logs tas-service --since 10h 2>&1 | grep -aiE "error|fail" | grep -a "3e384fa2" | tail -10;
echo '===EMQX_TASK_MSG===';
echo 'Tksc@197653' | sudo -S docker logs tas-emqx-service --since 10h 2>&1 | grep -a "3e384fa2" | tail -10;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
