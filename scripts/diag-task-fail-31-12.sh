#!/usr/bin/env bash
# 3.1任务失败排查（十二）：挖 fh_wayline_execute 实现调的 SDK 类/方法
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
python3 - <<'PYEOF'
import zipfile, re
z = zipfile.ZipFile('/tmp/mcp31.jar')
# 1) wayline 工具类
names = [n for n in z.namelist() if re.search(r'tools/wayline/.*\.class$', n)]
print('wayline tool classes:', names)
for n in names:
    data = z.read(n)
    strs = sorted(set(re.findall(rb'[ -~]{6,90}', data)))
    hits = [s.decode() for s in strs if re.search(rb'execute|Module|method|uuid|wayline', s, re.I)]
    if hits:
        print('---', n)
        for h in hits[:40]:
            print('   ', h)
# 2) MCP 内嵌 SDK 的 FlightTaskModule 是否有 executeFlightTask
sdk = [n for n in z.namelist() if n.endswith('com/dji/flighthubsdk/core/FlightTaskModule.class')]
for n in sdk:
    data = z.read(n)
    strs = sorted(set(re.findall(rb'[ -~]{6,60}', data)))
    hits = [s.decode() for s in strs if re.search(rb'^execute|executeFlight|FlightTask', s)]
    print('=== SDK FlightTaskModule in', n)
    for h in hits[:20]:
        print('   ', h)
PYEOF
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
