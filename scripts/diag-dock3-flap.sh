#!/usr/bin/env bash
# 机场日抛分析：今天OSD按分钟分布（掉线/恢复时刻）+ 96h在线状态翻转历史 + 当前状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
SN=8UUXN2K00A0144;
echo ====NOW_EMQX====;
docker exec emqx emqx_ctl clients list 2>/dev/null | grep -a 8UUX | grep -aoE "peername=[^,]+|connected_at=[0-9]+|connected=(true|false)";
echo ====NOW_REDIS_TOPO====;
docker exec redis redis-cli GET "common_310_device:topo:$SN" | head -c 300; echo;
echo ====OSD_PER_MINUTE_6H====;
docker logs --since 6h tas-service 2>&1 | grep -a "RabbitmqPublishSuccess" | grep -a "\\\"sn\\\":\\\"$SN\\\"" | grep -a device_osd | sed -E "s/.*@timestamp\\\":[0-9]+\\\.[0-9]+Z,$//; s/.*time_stamp\\\":[0-9]+,//" | sed -E "s/.*\\\"@timestamp\\\":\\\"([^\\\"]+).*/\\1/" | cut -c1-16 | uniq -c | tail -40;
echo ====ONLINE_FLIP_HISTORY_96H====;
docker logs --since 96h es-management 2>&1 | grep -a "SN:$SN" | grep -a "DeviceOnlineStatus:" | sed -E "s/.*\\\"time\\\":\\\"([^\\\"]+)\\\".*DeviceOnlineStatus:(true|false).*/\\1 \\2/" | awk "{if(\$2!=p){print \$1,\$2; p=\$2}}" | tail -30'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
