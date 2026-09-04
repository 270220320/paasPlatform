#!/usr/bin/env bash
# 媒体现状复查（五）：MinIO 03:30 后新文件定位 + 媒体表记录 + 回调 file_id/object_key
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===MINIO_NEW_FILES===;
docker exec minio sh -c "find /data/file-storage-privatization -type d -newermt 2026-08-25\ 03:30 2>/dev/null | head -12";
echo ===CALLBACK_FILE_FIELDS===;
docker logs tas-service --since 40m 2>&1 | grep -a file_upload_callback | grep -a "03:37:3" | grep -a "receive emqx" | head -4 | grep -ao "\"file_id\":\"[^\"]*\|\"object_key\":\"[^\"]*\|\"name\":\"[^\"]*\|\"module\":[0-9]*\|\"size\":[0-9]*" | sort -u;
echo ===ES_TASK_TABLES===;
docker exec postgres_instance_1 psql -U app_rw -d es_task -c "\dt" 2>/dev/null;
echo ===MEDIA_ROWS_TODAY===;
docker exec postgres_instance_1 psql -U app_rw -d es_task -c "SELECT table_name FROM information_schema.tables WHERE table_schema='"'"'public'"'"' AND table_name LIKE '"'"'%media%'"'"';" 2>/dev/null;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
