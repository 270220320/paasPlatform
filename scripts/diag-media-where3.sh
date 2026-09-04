#!/usr/bin/env bash
# 媒体位置诊断（三）：最小化验证——日志流是否在动 + 直接抓 OSD 行
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===LOG_VOLUME_10M===;
docker logs tas-service --since 10m 2>&1 | wc -l;
echo ===LAST_3_LINES===;
docker logs tas-service --since 10m 2>&1 | tail -3 | cut -c1-200;
echo ===OSD_LINES_10M===;
docker logs tas-service --since 10m 2>&1 | grep -ac "/osd";
echo ===REMAIN_60M===;
docker logs tas-service --since 60m 2>&1 | grep -ac remain_upload;
echo ===EMQX_DOCK_CLIENT===;
docker exec emqx emqx ctl clients list 2>/dev/null | grep -a 8UUXN2K00A0144 | head -3;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
