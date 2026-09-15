#!/usr/bin/env bash
# 219015 排查（六）：es-task 是否消费 flighttask_ready + RabbitMQ 队列/消费者状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===ES_TASK_READY_CONSUME===';
echo 'Tksc@197653' | sudo -S docker logs es-task --since 6h 2>&1 | grep -ac "flighttask_ready";
echo '---detail---';
echo 'Tksc@197653' | sudo -S docker logs es-task --since 6h 2>&1 | grep -a "flighttask_ready" | tail -3 | cut -c1-600;
echo '===RABBIT_CONTAINER===';
echo 'Tksc@197653' | sudo -S docker ps --format '{{.Names}}' 2>/dev/null | grep -aiE 'rabbit|mq';
echo '===RABBIT_QUEUES===';
RC=$(echo 'Tksc@197653' | sudo -S docker ps --format '{{.Names}}' 2>/dev/null | grep -aiE 'rabbit' | head -1);
if [ -n "$RC" ]; then
  echo 'Tksc@197653' | sudo -S docker exec $RC rabbitmqctl list_queues name messages consumers 2>&1 | grep -aiE 'task|event|notify|ready' | head -15;
fi;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
