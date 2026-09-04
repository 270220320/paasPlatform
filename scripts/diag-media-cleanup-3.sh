#!/usr/bin/env bash
# 媒体清理侦察（三）：任务后最新 OSD——机场/飞机存储、待上传数、媒体搬运状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===LATEST_OSD_STORAGE===;
docker logs tas-service --since 10m 2>&1 | grep -a "osd" | grep -ao "\\"storage\\":{\[^}]*}" | sort | uniq -c | tail -6;
echo ===LATEST_REMAIN_UPLOAD===;
docker logs tas-service --since 10m 2>&1 | grep -ao "\\"media_file_detail\\":{\[^}]*}\|\\"remain_upload\\":[0-9]*\|\\"uploading\\":[a-z]*\|\\"media_uploading\\":[a-z]*" | sort | uniq -c;
echo ===DEVICE_DETAIL_NOW===;
docker logs pf-device-service --since 30m 2>&1 | grep -a "device/detail" | tail -1 | grep -ao "\\"storage\\":{\[^}]*}\|\\"media_file_detail\\":{\[^}]*}\|\\"used\\":[0-9]*" | sort | uniq -c;
echo ===SD_COPY_OR_FILESYNC_EVENTS===;
docker logs tas-service --since 60m 2>&1 | grep -aiE "sd_card|file_sync|media_sync|copy|pull_files|file_pull" | grep -av flight_replay | tail -6;
echo ===AIRPORT_OSD_MEDIA_FIELDS===;
docker logs tas-service --since 10m 2>&1 | grep -a "8UUXN2K00A0144" | grep -ao "\\"media[^,}]*\|\\"file[^,}]*\\"[^,}]*" | sort | uniq -c | head -12;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
