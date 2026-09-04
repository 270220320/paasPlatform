#!/usr/bin/env bash
# 今日 OSD 分钟分布（修正转义）：还原机场停报与恢复时刻
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
SN=8UUXN2K00A0144;
echo ====OSD_PER_MIN_12H====;
docker logs --since 12h tas-service 2>&1 | grep -a device_osd | grep -a "\"sn\":\"$SN\"" | grep -aoE "\"@timestamp\":\"[^\"]+\"" | cut -c15-30 | uniq -c | tail -45;
echo ====HMS_PER_MIN_3H====;
docker logs --since 3h tas-service 2>&1 | grep -a device_hms | grep -a "\"sn\":\"$SN\"" | grep -aoE "\"@timestamp\":\"[^\"]+\"" | cut -c15-30 | uniq -c | tail -30;
echo ====DONE===='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
