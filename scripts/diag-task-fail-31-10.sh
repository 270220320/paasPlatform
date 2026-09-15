#!/usr/bin/env bash
# 3.1任务失败排查（十）：MCP jar的execute实现 + atc/tas全量日志窗口
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===MCP_JAR_TASK_CLASSES===';
echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp sh -c 'cd /tmp && jar tf /app/flighthub-mcp.jar 2>/dev/null | grep -iE "FlightTask" | head -20 || unzip -l /app/flighthub-mcp.jar 2>/dev/null | grep -iE "FlightTask" | head -20' 2>&1 | head -22;
echo '===MCP_EXEC_STRINGS===';
echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp sh -c 'command -v unzip >/dev/null && (cd /tmp && unzip -o -q /app/flighthub-mcp.jar "com/dji/flighthubsdk/core/FlightTaskModule.class" -d /tmp/mcpx 2>/dev/null; strings /tmp/mcpx/com/dji/flighthubsdk/core/FlightTaskModule.class 2>/dev/null | grep -iE "execute|create|update.*[Ss]tatus" | head -20) || echo no-unzip' 2>&1;
echo '===JNI_SO_EXECUTE===' ;
echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp sh -c 'strings /tmp/flighthub_sdk_deps*/libflighthubsdk_jni.so 2>/dev/null | grep -E "^executeFlightTask$|executeFlightTask" | head -5' 2>&1;
echo '===ATC_LOG_WINDOW===';
echo 'Tksc@197653' | sudo -S docker logs atc-service --since 48h 2>&1 | grep -a "08:5[6-9]:" | grep -aiE "task|execute|error|fail" | head -25;
echo '===TAS_LOG_WINDOW_ALL===' ;
echo 'Tksc@197653' | sudo -S docker logs tas-service --since 48h 2>&1 | grep -a "08:5[6-8]:" | grep -avE "heartbeat|osd|update_topo" | tail -25;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
