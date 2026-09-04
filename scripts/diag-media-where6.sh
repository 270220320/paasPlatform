#!/usr/bin/env bash
# 媒体位置诊断（六）：适配转义 JSON——storage/media_file_detail/drone_in_dock 最终快照
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
cat > /tmp/osd3.py <<'PYEOF'
import subprocess, re
logs = subprocess.run("echo Tksc@197653 | sudo -S docker logs tas-service --since 60m 2>&1", shell=True, capture_output=True, text=True).stdout
dock, drone = [], []
for line in logs.splitlines():
    if '"thing/product/8UUXN2K00A0144/osd"' in line: dock.append(line)
    elif '"thing/product/1581F8HGX252500A009X/osd"' in line: drone.append(line)
def ts(line):
    m = re.search(r'"@timestamp":"([^"]+)"', line)
    return m.group(1) if m else '?'
def find_last(lines, pat):
    for line in reversed(lines):
        m = re.search(pat, line)
        if m:
            return ts(line), m.group(0)
    return None, None
P_STOR = re.compile(r'storage..:.total.:(\d+),.used.:(\d+)')
print('== DOCK (last known within 60m) ==')
t, v = find_last(dock, r'storage..:.total.:\d+,.used.:\d+')
if t:
    m = P_STOR.search(v)
    print('storage @', t, '| used = %.1f MB / total = %.1f GB' % (int(m.group(2))/1024, int(m.group(1))/1024/1024))
t, v = find_last(dock, r'media_file_detail..:[^}]*}')
print('media_file_detail @', t, ':', v)
t, v = find_last(dock, r'drone_in_dock.:\d+')
print('drone_in_dock @', t, ':', v)
t, v = find_last(dock, r'mode_code.:\d+')
print('dock mode_code @', t, ':', v)
print('== DRONE (last known within 60m) ==')
t, v = find_last(drone, r'storage..:.total.:\d+,.used.:\d+')
if t:
    m = P_STOR.search(v)
    print('storage @', t, '| used = %.1f MB / total = %.1f GB' % (int(m.group(2))/1024, int(m.group(1))/1024/1024))
else:
    print('storage: not reported in window')
t, v = find_last(drone, r'mode_code.:\d+')
print('drone mode_code @', t, ':', v)
PYEOF
python3 /tmp/osd3.py
echo ===DONE===
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
