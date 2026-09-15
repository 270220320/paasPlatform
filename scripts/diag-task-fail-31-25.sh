#!/usr/bin/env bash
# 219015 排查（十）：unsupported 确切拼写/上下文 + is failed 格式串 + RabbitMQ 完整绑定
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
echo '===UNSUPPORTED_ALL===';
S docker exec es-task sh -c "tr -c '[:print:]' '\n' < /app/http_main" | grep -ai 'unsupported' | sort -u | head -40;
echo '===IS_FAILED_FMT===';
S docker exec es-task sh -c "tr -c '[:print:]' '\n' < /app/http_main" | grep -a 'is failed' | sort -u | head -20;
echo '===READY_FAILED_LOG===';
S docker logs es-task --since 24h 2>&1 | grep -a 'flighttask_ready' | tail -4 | cut -c1-1000;
echo '===RABBIT_BINDINGS_ALL===';
S docker exec rabbitmq rabbitmqctl list_bindings --silent source_name destination_name routing_key 2>&1 | grep -aiE 'es-task|event|notify' | head -40;
echo '===RABBIT_QUEUES_TASK===';
S docker exec rabbitmq rabbitmqctl list_queues --silent name messages consumers 2>&1 | grep -aiE 'task|event|notify|ready|wayline' | head -20;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
