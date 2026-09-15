#!/usr/bin/env bash
# 219015 排查（三）：value 包全量枚举 + FlightTaskStatus 数值反编译
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
python3 - <<'PYEOF'
import zipfile, re, io
z = zipfile.ZipFile('/tmp/mcp31.jar')
inner = zipfile.ZipFile(io.BytesIO(z.read('BOOT-INF/lib/flight-hub-sdk-3.1.1.jar')))
vals = sorted(n for n in inner.namelist() if n.startswith('com/dji/flighthubsdk/value/') and n.endswith('.class'))
print('ALL value enums:')
for n in vals:
    print('  ', n.split('/')[-1])
PYEOF
cd /tmp
python3 - <<'PYEOF'
import zipfile, io
z = zipfile.ZipFile('/tmp/mcp31.jar')
inner = zipfile.ZipFile(io.BytesIO(z.read('BOOT-INF/lib/flight-hub-sdk-3.1.1.jar')))
target = None
for n in inner.namelist():
    if n.endswith('/value/FlightTaskStatus.class') or n.endswith('/value/TaskStatus.class'):
        target = n
        break
if target:
    open('/tmp/FlightTaskStatus.class', 'wb').write(inner.read(target))
    print('extracted', target)
PYEOF
JAVAP=/usr/lib/jvm/java-21-openjdk-amd64/bin/javap
if [ -f /tmp/FlightTaskStatus.class ]; then
  echo '===FlightTaskStatus_STATIC_INIT===';
  $JAVAP -c -p /tmp/FlightTaskStatus.class 2>/dev/null | sed -n '/static {}/,/^$/p' | grep -E 'String|bipush|sipush|iconst|invokespecial' | head -60;
  echo '===ENUM_CONSTANTS===';
  $JAVAP -p /tmp/FlightTaskStatus.class 2>/dev/null | head -30;
fi
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
