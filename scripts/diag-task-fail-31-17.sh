#!/usr/bin/env bash
# 219015 排查（二）：MCP mock模式检查 + SDK 3.1.1 状态枚举/UpdateTaskStatusReq 反编译
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===MCP_ENV_MOCK===';
echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp printenv 2>/dev/null | grep -iE 'mock|mode|sdk' ;
echo '===SDK_ENUMS===';
python3 - <<'PYEOF'
import zipfile, re, io
z = zipfile.ZipFile('/tmp/mcp31.jar')
inner = zipfile.ZipFile(io.BytesIO(z.read('BOOT-INF/lib/flight-hub-sdk-3.1.1.jar')))
names = [n for n in inner.namelist() if re.search(r'(Status|TaskStatus|FlightTask)', n) and n.endswith('.class')]
print('status-related classes:')
for n in sorted(names)[:20]:
    print('  ', n)
for n in sorted(names):
    if re.search(r'(UpdateTaskStatusReq|TaskStatus\w*|FlightTaskStatus\w*)\.class$', n):
        data = inner.read(n)
        strs = sorted(set(re.findall(rb'[ -~]{4,70}', data)))
        print('=== %s' % n)
        for s in strs:
            t = s.decode()
            if re.match(r'^[A-Za-z_][A-Za-z0-9_]*$', t) or ':' in t or '=' in t:
                print('   ', t)
PYEOF
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
