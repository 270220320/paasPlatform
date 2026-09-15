#!/usr/bin/env bash
# 司空3.1升级（二十五）：清空飞行计划库（wayline_files + files 关联 + MinIO kmz）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===FILES_TABLE_LOOKUP===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U app_rw -d file_storage -Atc \"SELECT column_name FROM information_schema.columns WHERE table_name='files'\"" 2>&1 | tr '\n' ' ';
echo;
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U app_rw -d file_storage -c \\\"SELECT * FROM files WHERE id=1\\\"" 2>&1 | head -8;
echo '===OTHER_MINIO_DIR_PEEK===';
echo 'Tksc@197653' | sudo -S docker exec minio sh -c 'ls -R /data/file-storage-privatization/6da41ab5-07e8-476d-927d-4ee21f5ff967/ 2>/dev/null | head -5; ls /data/file-storage-privatization/bd8ad60a-6f01-4e62-b04b-00af260974a3/ 2>/dev/null';
echo '===DELETE_WAYLINE_ROWS===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U app_rw -d file_storage -c 'DELETE FROM wayline_files'" 2>&1;
echo '===DELETE_FILE_ROWS===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U app_rw -d file_storage -c \\\"DELETE FROM files WHERE id=1\\\"" 2>&1;
echo '===DELETE_MINIO_KMZ===';
echo 'Tksc@197653' | sudo -S docker exec minio sh -c 'rm -rf /data/file-storage-privatization/bd8ad60a-6f01-4e62-b04b-00af260974a3 && echo kmz_deleted';
echo '===RESTART_FILE_STORAGE===';
echo 'Tksc@197653' | sudo -S docker restart file-storage 2>&1;
sleep 10;
echo '===VERIFY===';
echo "wayline_files=$(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U app_rw -d file_storage -Atc 'SELECT count(*) FROM wayline_files'" 2>/dev/null)";
echo "files_id1=$(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U app_rw -d file_storage -Atc 'SELECT count(*) FROM files WHERE id=1'" 2>/dev/null)";
echo 'Tksc@197653' | sudo -S docker exec minio sh -c 'ls /data/file-storage-privatization/' 2>/dev/null;
echo "file_storage_health=$(echo 'Tksc@197653' | sudo -S docker inspect file-storage --format '{{.State.Health.Status}}' 2>/dev/null)";
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
