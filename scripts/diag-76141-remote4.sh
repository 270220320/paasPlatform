#!/usr/bin/env bash
# 在 76.141 上执行：daemon.json live-restore 检查 + rabbitmq 端口映射
SUDO="sudo -S"
PW="Tksc@197653"
echo "===== DAEMON.JSON ====="
echo "$PW" | $SUDO cat /etc/docker/daemon.json 2>/dev/null
echo "===== RABBITMQ PORT MAP ====="
echo "$PW" | $SUDO docker port rabbitmq 2>/dev/null
echo "===== RABBITMG MTLS/AMQP LISTEN (netstat inside ns) ====="
echo "$PW" | $SUDO timeout 10 nsenter -t $(echo "$PW" | $SUDO docker inspect rabbitmq --format '{{.State.Pid}}' 2>/dev/null) -n ss -tln 2>/dev/null | head -8
