#!/usr/bin/env bash
# 媒体诊断（二）：司空任务详情媒体字段 + 媒体表记录 + MinIO 实际文件 + flighttask_create 请求体
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===PG_TABLES_MEDIA===;
docker exec postgres_instance_1 psql -U app_rw -d es_task -t -c "SELECT tablename FROM pg_tables WHERE tablename ~ $$media|task$$ ;" 2>&1 | head -25;
echo ===PG_TASKS_LATEST===;
docker exec postgres_instance_1 psql -U app_rw -d es_task -x -c "SELECT * FROM tasks ORDER BY id DESC LIMIT 1;" 2>&1 | head -60;
echo ===PG_MEDIA_ROWS===;
for T in $(docker exec postgres_instance_1 psql -U app_rw -d es_task -t -c "SELECT tablename FROM pg_tables WHERE tablename ~ $$media$$;" 2>/dev/null); do
  echo "--TABLE $T--";
  docker exec postgres_instance_1 psql -U app_rw -d es_task -x -c "SELECT * FROM $T ORDER BY 1 DESC LIMIT 3;" 2>&1 | head -40;
done;
echo ===MINIO_DATA_DIRS===;
docker exec minio sh -c "ls /data 2>/dev/null; ls /data/file-storage-privatization 2>/dev/null | tail -15" 2>&1;
echo ===TAS_CREATE_REQ===;
docker logs tas-service --since 100m 2>&1 | grep -a "flighttask_create" | tail -3;
echo ===FILE_UPLOAD_CALLBACK_ALL===;
docker logs tas-service --since 100m 2>&1 | grep -a "file_upload_callback" | grep -av "events_reply" | tail -6;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
