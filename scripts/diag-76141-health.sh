#!/usr/bin/env bash
# 诊断 76.141 服务器整体健康状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
echo "===== DATE ====="; date
echo "===== UPTIME/LOAD ====="; uptime
echo "===== DISK ====="; df -h | grep -vE "tmpfs|overlay|shm"
echo "===== MEMORY ====="; free -h
echo "===== DOCKER PS ====="; docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>&1 | head -40
' 2>&1
