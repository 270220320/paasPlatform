#!/usr/bin/env bash
# 诊断 314004：舱盖开后即关、任务终止。查设备侧服务在任务时间窗（UTC 01:33:40~01:34:35）的日志 + HMS
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
SN=8UUXN2K00A0144; FID=fae02bc8;
W1=2026-08-25T01:33:40; W2=2026-08-25T01:34:40;
for S in tas-service pf-control-core drtc-manager es-drc-service pf-device-service; do
  echo "====$S====";
  docker logs --since $W1 --until $W2 $S 2>&1 | grep -aE "$SN|$FID" | grep -aiE "error|fail|hms|cover|drone_on|power_on|rtk|ready|precheck|reject|timeout|abort" | tail -12;
done;
echo "====HMS_EVENTS====";
docker logs --since $W1 --until $W2 tas-service 2>&1 | grep -a device_hms | tail -20;
echo "====DONE===='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
