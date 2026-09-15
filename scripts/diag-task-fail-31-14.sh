#!/usr/bin/env bash
# 3.1任务失败排查（十四）：javap 反编译 FhWaylineExecuteTool 看 updateFlightTaskStatus 的状态码常量
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
cd /tmp
python3 - <<'PYEOF'
import zipfile
z = zipfile.ZipFile('/tmp/mcp31.jar')
for src, dst in [
    ('BOOT-INF/classes/com/dji/flighthub/mcp/tools/wayline/FhWaylineExecuteTool.class', '/tmp/FhWaylineExecuteTool.class'),
    ('BOOT-INF/classes/com/dji/flighthub/mcp/tools/wayline/FhWaylineControlTool.class', '/tmp/FhWaylineControlTool.class'),
]:
    open(dst, 'wb').write(z.read(src))
    print('extracted', dst)
PYEOF
JAVAP=$(ls /opt/jdk*/bin/javap 2>/dev/null || ls ~/jdk*/bin/javap 2>/dev/null | head -1)
echo "javap=$JAVAP";
echo '===EXEC_TOOL_BYTECODE===';
$JAVAP -c -p /tmp/FhWaylineExecuteTool.class 2>&1 | grep -B4 -A8 -iE 'updateFlightTaskStatus|bipush|sipush|iconst' | head -60;
echo '===CONTROL_TOOL_BYTECODE===';
$JAVAP -c -p /tmp/FhWaylineControlTool.class 2>&1 | grep -B4 -A8 -iE 'updateFlightTaskStatus|bipush|sipush|iconst|tableswitch|lookupswitch' | head -60;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
