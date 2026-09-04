#!/usr/bin/env bash
# 媒体位置诊断（二）：修正转义——机场/飞机 OSD storage、remain_upload 趋势
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===DOCK_OSD_LAST===;
docker logs tas-service --since 8m 2>&1 | grep -a "thing/product/8UUXN2K00A0144/osd" | tail -1 | grep -aoE "\"storage\":\{[^}]*\}|\"media_file_detail\":\{[^}]*\}|\"drone_in_dock\":[0-9]+|\"mode_code\":[0-9]+";
echo ===DRONE_OSD_LAST===;
docker logs tas-service --since 8m 2>&1 | grep -a "thing/product/1581F8HGX252500A009X/osd" | tail -1 | grep -aoE "\"storage\":\{[^}]*\}|\"mode_code\":[0-9]+|\"remain_photo_num\":[0-9]+";
echo ===REMAIN_UPLOAD_TREND_40M===;
docker logs tas-service --since 40m 2>&1 | grep -a remain_upload | grep -aoE "@timestamp":"[0-9T:-]+Z.*remain_upload":[0-9]+ | sed "s/.*@timestamp\":\"//; s/Z.*remain_upload/Z remain_upload/" | uniq -c | tail -8;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
