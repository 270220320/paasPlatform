#!/usr/bin/env bash
# 3.1任务失败排查（十三）：BOOT-INF/lib 内嵌 SDK jar 的 FlightTaskModule 方法清单
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
python3 - <<'PYEOF'
import zipfile, re, io
z = zipfile.ZipFile('/tmp/mcp31.jar')
libs = [n for n in z.namelist() if n.startswith('BOOT-INF/lib/') and re.search(r'flight|hub|dji', n, re.I)]
print('lib jars:', libs)
for lib in libs:
    inner = zipfile.ZipFile(io.BytesIO(z.read(lib)))
    cls = [n for n in inner.namelist() if n.endswith('core/FlightTaskModule.class')]
    for c in cls:
        data = inner.read(c)
        strs = sorted(set(re.findall(rb'[ -~]{5,60}', data)))
        print('=== %s :: %s' % (lib, c))
        for s in strs:
            t = s.decode()
            if re.match(r'^[a-z][a-zA-Z0-9_]*$', t) and re.search(r'execute|create|delete|edit|update|cancel|flight|task|Flight|Task', t):
                print('   ', t)
PYEOF
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
