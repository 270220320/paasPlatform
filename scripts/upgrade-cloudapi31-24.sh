#!/usr/bin/env bash
# 司空3.1升级（二十四）：查看飞行计划库数据量与内容
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===WAYLINE_FILES_COUNT===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U app_rw -d file_storage -Atc 'SELECT count(*) FROM wayline_files'" 2>&1;
echo '===WAYLINE_FILES_COLS===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U app_rw -d file_storage -Atc \"SELECT column_name FROM information_schema.columns WHERE table_name='wayline_files'\"" 2>&1 | tr '\n' ' ';
echo;
echo '===WAYLINE_FILES_ROWS===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U app_rw -d file_storage -c \"SELECT * FROM wayline_files LIMIT 10\"" 2>&1 | head -25;
echo '===WAYLINE_COPY_COUNT===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U app_rw -d file_storage -Atc 'SELECT count(*) FROM wayline_copy_tasks'" 2>&1;
echo '===FLIGHT_TASKS_COUNT===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 sh -c "psql -U app_rw -d es_task -Atc 'SELECT count(*) FROM flight_tasks'" 2>&1;
echo '===MINIO_WAYLINE_DIR===';
echo 'Tksc@197653' | sudo -S docker exec minio sh -c 'ls /data/file-storage-privatization/ 2>/dev/null; ls /data/file-storage-privatization/wayline* 2>/dev/null | head -15' 2>&1;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
