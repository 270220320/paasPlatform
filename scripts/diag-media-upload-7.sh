#!/usr/bin/env bash
# 媒体诊断（七）：补传队列推进进度（最近架次日期）+ MinIO 最新目录
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo ===RECENT_CALLBACKS_TIMELINE===;
echo Tksc@197653 | sudo -S docker logs tas-service --since 45m 2>&1 | grep -a "file_upload_callback" | grep -a "receive emqx" | grep -ao '"created_time":"[^"]*"\|"flight_id":"[^"]*"' | paste - - | tail -12;
echo ===MINIO_NEWEST_DIRS===;
echo Tksc@197653 | sudo -S docker exec minio sh -c "ls -lat /data/file-storage-privatization | head -8";
echo ===CALLBACK_RATE===;
echo Tksc@197653 | sudo -S docker logs tas-service --since 45m 2>&1 | grep -a "file_upload_callback" | grep -ac "receive emqx";
echo ===DONE===
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
