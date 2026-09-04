#!/usr/bin/env bash
# 媒体位置诊断：最新机场/飞机 OSD storage 对比 + 待上传数 + 无人机在舱状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===LATEST_DOCK_OSD_STORAGE===;
docker logs tas-service --since 6m 2>&1 | grep -a "\"gateway\":\"8UUXN2K00A0144\",\"tid" | grep -ao "\\"storage\\":{\[^}]*}\|\\"media_file_detail\\":{\[^}]*}\|\\"drone_in_dock\\":[0-9]*\|\\"mode_code\\":[0-9]*" | sort | uniq -c | tail -8;
echo ===LATEST_DRONE_OSD_STORAGE===;
docker logs tas-service --since 6m 2>&1 | grep -a "\"gateway\":\"1581F8HGX252500A009X\"" | grep -ao "\\"storage\\":{\[^}]*}\|\\"mode_code\\":[0-9]*" | sort | uniq -c | tail -6;
echo ===DEVICE_DETAIL_SNAPSHOT===;
D=$(docker logs pf-device-service --since 15m 2>&1 | grep -a "device/detail" | tail -1);
echo "$D" | grep -ao "\\"storage\\":{\[^}]*}" | head -4;
echo "$D" | grep -ao "\\"media_file_detail\\":{\[^}]*}\|\\"drone_in_dock\\":[0-9]*\|\\"remain_photo_num\\":[0-9]*\|\\"remain_record_duration\\":[0-9]*" | head -6;
echo ===REMAIN_UPLOAD_TREND===;
docker logs tas-service --since 30m 2>&1 | grep -a "remain_upload" | grep -ao "@timestamp\":\"[0-9T:-]*Z\|remain_upload\":[0-9]*" | paste - - | tail -6;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
