#!/usr/bin/env bash
# 在 76.141 上执行：dockerd socket 泄漏对端分析 + rabbitmq 队列规模
SUDO="sudo -S"
PW="Tksc@197653"

echo "===== DOCKERD TCP SOCKET STATES ====="
echo "$PW" | $SUDO ss -tnp 2>/dev/null | grep 'pid=1266' | awk '{print $1}' | sort | uniq -c

echo "===== DOCKERD TCP PEER COUNT ====="
echo "$PW" | $SUDO ss -tnp 2>/dev/null | grep 'pid=1266' | awk '{print $5}' | sed 's/:[0-9]*$//' | sort | uniq -c | sort -rn | head -10

echo "===== DOCKERD UNIX SOCKETS ====="
echo "$PW" | $SUDO ss -xnp 2>/dev/null | grep -c 'pid=1266'

echo "===== RABBITMQ QUEUE COUNT (max 25s) ====="
echo "$PW" | $SUDO timeout 30 docker exec rabbitmq timeout 25 rabbitmqctl list_queues --quiet name 2>/dev/null | wc -l

echo "===== DOCKER.SOCK CLIENTS ====="
echo "$PW" | $SUDO ss -xn 2>/dev/null | grep -B1 'docker\.sock' | grep -oE 'pid=[0-9]+,[^ ]*' | sort | uniq -c | sort -rn | head -10
