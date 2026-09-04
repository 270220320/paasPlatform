#!/usr/bin/env bash
# 媒体位置诊断（五）：最后已知 dock/drone OSD 快照 + 机场 MQTT 在线状态 + Redis 设备快照
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
cat > /tmp/osd2.py <<'PYEOF'
import subprocess, json, re
logs = subprocess.run("echo Tksc@197653 | sudo -S docker logs tas-service --since 120m 2>&1", shell=True, capture_output=True, text=True).stdout
dock, drone = [], []
last_dock_ts, last_any = None, None
for line in logs.splitlines():
    if '8UUXN2K00A0144' not in line: continue
    m = re.search(r'"@timestamp":"([^"]+)"', line)
    if m: last_any = m.group(1)
    if '"thing/product/8UUXN2K00A0144/osd"' in line: dock.append(line)
    if '"thing/product/1581F8HGX252500A009X' in line and '/osd"' in line: drone.append(line)
print('last_any_ts_of_dock_traffic:', last_any)
print('dock_osd_lines:', len(dock), '| drone_osd_lines:', len(drone))
def stor(line):
    for pat in [r'"storage":\{"total":(\d+),"used":(\d+)\}', r'"storage":\{"total":(\d+),"used":(\d+)']:
        m = re.search(pat, line)
        if m: return int(m.group(2)), int(m.group(1))
    return None
if dock:
    print('DOCK storage (last found):')
    for line in reversed(dock):
        s = stor(line)
        if s:
            ts = re.search(r'"@timestamp":"([^"]+)"', line)
            print('  @', ts.group(1) if ts else '?', 'used_MB:', s[0]/1024, 'total_MB:', s[1]/1024)
            break
    mf = [l for l in dock if 'media_file_detail' in l]
    if mf:
        ts = re.search(r'"@timestamp":"([^"]+)"', mf[-1])
        r2 = re.search(r'"media_file_detail":\{[^}]*\}', mf[-1])
        print('DOCK media_file_detail last @', ts.group(1) if ts else '?', ':', r2.group(0) if r2 else '?')
    did = [l for l in dock if 'drone_in_dock' in l]
    if did:
        ts = re.search(r'"@timestamp":"([^"]+)"', did[-1])
        r3 = re.search(r'"drone_in_dock":\d+', did[-1])
        print('DOCK drone_in_dock last @', ts.group(1) if ts else '?', ':', r3.group(0) if r3 else '?')
if drone:
    print('DRONE storage (last found):')
    for line in reversed(drone):
        s = stor(line)
        if s:
            ts = re.search(r'"@timestamp":"([^"]+)"', line)
            print('  @', ts.group(1) if ts else '?', 'used_MB:', s[0]/1024, 'total_MB:', s[1]/1024)
            break
PYEOF
python3 /tmp/osd2.py
echo ===EMQX_CLIENT_DOCK===;
echo 'Tksc@197653' | sudo -S docker exec emqx sh -c "emqx ctl clients list 2>/dev/null || /opt/emqx/bin/emqx ctl clients list 2>/dev/null" | grep -aiE "8UUXN2K00A0144|1581F8HG" | cut -c1-160;
echo ===TAS_LAST_2M===;
echo 'Tksc@197653' | sudo -S docker logs tas-service --since 3m 2>&1 | grep -ac 8UUXN2K00A0144;
echo ===DONE===
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
