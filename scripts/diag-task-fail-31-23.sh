#!/usr/bin/env bash
# 219015 排查（八）：es-task 二进制字符串上下文 + wayline_task_created 消费方扫描
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
echo '===CONTAINERS===';
S docker ps --format '{{.Names}}';
echo '===ES_TASK_BIN===';
S docker exec es-task sh -c 'ls -la /app 2>/dev/null; echo ---; readlink -f /proc/1/exe';
echo '===UNSUPPORTED_CTX===';
BIN=$(S docker exec es-task sh -c 'readlink -f /proc/1/exe' 2>/dev/null);
echo "BIN=$BIN";
S docker exec es-task sh -c "tr -c '[:print:]' '\n' < $BIN" | grep -a -B4 -A4 'task type unsupported' | head -60;
echo '===SYMBOLS_CONSUMER===';
S docker exec es-task sh -c "tr -c '[:print:]' '\n' < $BIN" | grep -aoE '[a-zA-Z0-9_./-]{3,}(Consumer|consumer)[a-zA-Z0-9_./-]{0,40}' | sort -u | head -30;
echo '===SYMBOLS_EVENT===';
S docker exec es-task sh -c "tr -c '[:print:]' '\n' < $BIN" | grep -aoE '[a-zA-Z0-9_./-]{3,}(flighttask_ready|FlightTaskReady)[a-zA-Z0-9_./-]{0,40}' | sort -u | head -20;
echo '===WAYLINE_TASK_CREATED_SCAN===';
for c in $(S docker ps --format '{{.Names}}' | grep -aiE 'es-|tas|atc|gateway|api|task|wayline'); do
  cnt=$(S docker logs "$c" --since 24h 2>&1 | grep -ac 'wayline_task_created');
  echo "$c => $cnt";
done;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
