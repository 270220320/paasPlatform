#!/usr/bin/env bash
# 媒体位置诊断（四）：python 解析最新 OSD——机场/飞机 storage、remain_upload 序列
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
cat > /tmp/osd.py <<'PYEOF'
import subprocess, json, re
logs = subprocess.run("echo Tksc@197653 | sudo -S docker logs tas-service --since 45m 2>&1", shell=True, capture_output=True, text=True).stdout
dock_osd, drone_osd, remain = [], [], []
for line in logs.splitlines():
    if '"topic":"thing/product/8UUXN2K00A0144/osd"' in line and '"storage"' in line:
        dock_osd.append(line)
    if '"topic":"thing/product/1581F8HGX252500A009X/osd"' in line and '"storage"' in line:
        drone_osd.append(line)
    m = re.search(r'"@timestamp":"([^"]+)"', line)
    if m and 'remain_upload' in line:
        r = re.search(r'"remain_upload":(\d+)', line)
        if r: remain.append((m.group(1), int(r.group(1))))
print('dock_osd_lines:', len(dock_osd), '| drone_osd_lines:', len(drone_osd))
def parse(line):
    m = re.search(r'"body":"(\{.*\})","body_ext"', line)
    if not m: return None
    raw = m.group(1)
    try: return json.loads(raw)
    except Exception:
        try: return json.loads(raw.replace('\\"','"'))
        except Exception: return None
if dock_osd:
    d = parse(dock_osd[-1])
    if d:
        data = d.get('data', d)
        print('DOCK last @', d.get('@timestamp',''), {
            'storage': data.get('storage'),
            'media_file_detail': data.get('media_file_detail'),
            'drone_in_dock': data.get('drone_in_dock'),
            'mode_code': data.get('mode_code'),
        })
if drone_osd:
    d = parse(drone_osd[-1])
    if d:
        data = d.get('data', d)
        print('DRONE last:', {
            'storage': data.get('storage'),
            'mode_code': data.get('mode_code'),
        })
print('remain_upload series (last 6):', remain[-6:])
vals = sorted(set(v for _, v in remain))
print('remain_upload distinct values:', vals)
PYEOF
python3 /tmp/osd.py
echo ===DONE===
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
