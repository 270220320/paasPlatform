#!/usr/bin/env bash
# 拉回 class 到 WSL 本地 javap 反编译
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
setsid -w scp -o StrictHostKeyChecking=no test@192.168.76.141:/tmp/FhWaylineExecuteTool.class /tmp/FhWaylineExecuteTool.class
setsid -w scp -o StrictHostKeyChecking=no test@192.168.76.141:/tmp/FhWaylineControlTool.class /tmp/FhWaylineControlTool.class
JAVAP=/usr/lib/jvm/java-21-openjdk-amd64/bin/javap
echo '===EXEC_TOOL==='
$JAVAP -c -p /tmp/FhWaylineExecuteTool.class 2>&1 | grep -B3 -A6 -E 'updateFlightTaskStatus|sipush|bipush' | head -50
echo '===CONTROL_TOOL==='
$JAVAP -c -p /tmp/FhWaylineControlTool.class 2>&1 | grep -B3 -A6 -E 'updateFlightTaskStatus|sipush|bipush|lookupswitch' | head -60
