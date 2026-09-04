#!/usr/bin/env bash
# 在 76.141 上执行：56789 归属 + rabbitmq 日志 + healthcheck 状态
SUDO="sudo -S"
PW="Tksc@197653"

echo "===== TRAEFIK PORTS ====="
echo "$PW" | $SUDO docker ps --format '{{.Names}} -> {{.Ports}}' 2>/dev/null | grep -i traefik

echo "===== K3S SVC 56789 ====="
echo "$PW" | $SUDO k3s kubectl get svc -A 2>/dev/null | grep -E '56789|NAME' | head -10

echo "===== K3S NS LIST ====="
echo "$PW" | $SUDO k3s kubectl get ns 2>/dev/null | head -15

echo "===== RABBITMQ LOG FILES ====="
echo "$PW" | $SUDO docker exec rabbitmq sh -c 'ls -la /var/log/rabbitmq/ 2>/dev/null; tail -20 /var/log/rabbitmq/rabbit@*.log 2>/dev/null' | tail -25

echo "===== RABBITMQ PROCESS INSIDE ====="
echo "$PW" | $SUDO timeout 15 docker exec rabbitmq sh -c 'ps aux | head -5' 2>&1

echo "===== UNHEALTHY CHECK ====="
echo "$PW" | $SUDO docker ps --filter health=unhealthy --format '{{.Names}} {{.Status}}' 2>/dev/null
echo "(empty means all healthy)"

echo "===== DOCKERD MEM/GOROUTINE HINT ====="
echo "$PW" | $SUDO cat /proc/1266/status 2>/dev/null | grep -E 'Threads|VmRSS'
