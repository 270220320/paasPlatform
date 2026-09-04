#!/usr/bin/env bash
# 诊断 76.141 高负载来源 + sudo docker 容器状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
echo "===== CPU CORES ====="; nproc
echo "===== TOP CPU PROCS ====="; ps aux --sort=-%cpu | head -15
echo "===== TOP MEM PROCS ====="; ps aux --sort=-%mem | head -10
echo "===== SUDO DOCKER PS ====="; echo "Tksc@197653" | sudo -S docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.RunningFor}}" 2>/dev/null | head -40
' 2>&1
