#!/usr/bin/env bash
# 3.1任务失败排查（十一）：MCP jar 拷出分析 execute 实现 + 3.1 安装目录找 SDK jar
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S cp $(echo 'Tksc@197653' | sudo -S docker inspect pf-devkit-mcp --format '{{range .Mounts}}{{.Source}}{{end}}' 2>/dev/null | head -1) /dev/null 2>/dev/null;
echo '===DockerCP_JAR===';
echo 'Tksc@197653' | sudo -S docker cp pf-devkit-mcp:/app/flighthub-mcp.jar /tmp/mcp31.jar 2>&1;
sudo ls -la /tmp/mcp31.jar 2>/dev/null || ls -la /tmp/mcp31.jar;
echo '===PY_ANALYZE===';
python3 - <<'PYEOF'
import zipfile, re
z = zipfile.ZipFile('/tmp/mcp31.jar')
names = [n for n in z.namelist() if re.search(r'FlightTask(Module)?\.class$|FlightTaskTool|task.*Tool|Tool.*task', n, re.I)]
print('classes:', names[:15])
for n in names:
    if n.endswith('.class'):
        data = z.read(n)
        strs = set(re.findall(rb'[ -~]{6,80}', data))
        hits = sorted(s.decode() for s in strs if re.search(rb'execute|/flight-tasks|flighttask', s, re.I))
        if hits:
            print('---', n)
            for h in hits[:25]:
                print('   ', h)
PYEOF
echo '===SDK_JAR_IN_31_DIR===';
echo 'Tksc@197653' | sudo -S find /opt/cloudApi2_3.1 -maxdepth 4 \( -name '*sdk*.jar' -o -name '*devkit*' \) 2>/dev/null | head -10;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
