#!/usr/bin/env bash
# 219015 排查（九）：/app/http_main 二进制上下文 + es-task wayline_task_created 完整日志 + RabbitMQ 绑定
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
echo '===UNSUPPORTED_CTX===';
S docker exec es-task sh -c "tr -c '[:print:]' '\n' < /app/http_main" | grep -a -B6 -A6 'task type unsupported' | head -80;
echo '===WTC_LOGS===';
S docker logs es-task --since 24h 2>&1 | grep -a 'wayline_task_created' | tail -6 | cut -c1-900;
echo '===TASKTYPE_STRINGS===';
S docker exec es-task sh -c "tr -c '[:print:]' '\n' < /app/http_main" | grep -aoE '[a-zA-Z0-9_./ -]{0,30}(TaskType|task type)[a-zA-Z0-9_./ -]{0,50}' | sort -u | head -50;
echo '===READY_HANDLER_STRINGS===';
S docker exec es-task sh -c "tr -c '[:print:]' '\n' < /app/http_main" | grep -aoE '[a-zA-Z0-9_.]{0,30}(FlighttaskReady|flighttask_ready|DeviceEventNotify)[a-zA-Z0-9_.]{0,40}' | sort -u | head -30;
echo '===RABBIT_BINDINGS===';
S docker exec rabbitmq rabbitmqctl list_bindings source_name destination_name routing_key 2>&1 | grep -aiE 'task|event|notify|ready|wayline' | head -30;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
