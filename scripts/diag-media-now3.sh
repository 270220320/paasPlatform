#!/usr/bin/env bash
# 媒体现状复查（三）：MinIO 落库确认 + 03:37 上传回调 + 机场是否重启（决定"是否需要重启"的关键证据）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===MINIO_82D6_FILES===;
docker exec minio sh -c "ls -la /data/file-storage-privatization/82d606ec10d02a884c51e2719dfe2bb3/ 2>&1";
echo ===UPLOAD_CALLBACK_40M===;
docker logs tas-service --since 40m 2>&1 | grep -a file_upload_callback | grep -ao "@timestamp\":\"2026-08-25T[0-9:]*\|\"name\":\"[A-Za-z0-9_.-]*\|\"module\":[0-9]*\|\"size\":[0-9]*" | paste - - - - | tail -10;
echo ===DOCK_REBOOT_CHECK===;
docker logs tas-service --since 90m 2>&1 | grep -aiE "device_offline|topo.*8UUXN2K00A0144|8UUXN2K00A0144.*(login|register)" | tail -8;
echo ===DOCK_UPTIME===;
docker logs tas-service --since 90m 2>&1 | grep -a "acc_time" | grep -ao "acc_time[^,]*" | tail -3;
echo ===HIGHEST_PRIORITY_AGAIN===;
docker logs tas-service --since 60m 2>&1 | grep -ac highest_priority;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
