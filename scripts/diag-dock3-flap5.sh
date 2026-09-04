#!/usr/bin/env bash
# 96h 在线翻转历史（python 解析）+ 今早掉线瞬间日志 + 断开事件
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S -v 2>/dev/null
sudo bash <<'SUEOF'
cat > /tmp/flip.py <<'PYEOF'
import sys, re
prev = None
for line in sys.stdin:
    if isinstance(line, bytes):
        line = line.decode('utf-8', 'ignore')
    if 'SN:8UUXN2K00A0144 CompleteMachineSN' not in line:
        continue
    m1 = re.search(r'"time":"([^"]+)"', line)
    m2 = re.search(r'DeviceOnlineStatus:(true|false)', line)
    if m1 and m2:
        t, s = m1.group(1), m2.group(1)
        if s != prev:
            print(t, s)
            prev = s
PYEOF
echo ====FLIP_HISTORY_96H_ES_MGMT====
docker logs --since 96h es-management 2>&1 | python3 /tmp/flip.py | tail -40
echo ====TAS_EMQX_EVENTS_24H====
docker logs --since 24h tas-emqx-service 2>&1 | grep -aiE '8UUX|disconnect|connhook' | grep -avE 'access log|GET|POST /api' | tail -20
echo ====TAS_AROUND_0915_0930====
docker logs --since 24h tas-service 2>&1 | grep -aE '2026-08-25T01:1[5-9]|2026-08-25T01:2[0-5]' | grep -a 8UUX | grep -avE 'device_osd|RabbitmqPublish|IsBestGateway' | tail -15
echo ====PF_DEVICE_ONOFF_96H====
docker logs --since 96h pf-device-service 2>&1 | grep -a 8UUXN2K00A0144 | grep -aiE 'offline_time|device_online_status|update.*online' | tail -12
echo ====DONE====
SUEOF
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
