#!/usr/bin/env bash
# 诊断 76.141：dockerd socket 泄漏对端分析 + rabbitmq 队列规模
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
echo "===== DOCKERD SOCKET PEERS ====="; echo "Tksc@197653" | sudo -S bash -c "ss -tnp 2>/dev/null | grep \"pid=1266\" | awk "{print \$5}\" | sed "s/:[0-9]*$//" | sort | uniq -c | sort -rn | head -10" 2>/dev/null
echo "===== DOCKERD SOCKET STATES ====="; echo "Tksc@197653" | sudo -S bash -c "ss -tnp 2>/dev/null | grep \"pid=1266\" | awk "{print \$1}" | sort | uniq -c" 2>/dev/null
echo "===== UNIX SOCKETS OF DOCKERD ====="; echo "Tksc@197653" | sudo -S bash -c "ss -xnp 2>/dev/null | grep -c \"pid=1266\"" 2>/dev/null
echo "===== RABBITMQ QUEUE COUNT (timeout 25s) ====="; echo "Tksc@197653" | sudo -S docker exec rabbitmq timeout 25 rabbitmqctl list_queues --quiet name 2>/dev/null | wc -l
echo "===== RABBITMQ DIAG ====="; echo "Tksc@197653" | sudo -S timeout 20 docker exec rabbitmq rabbitmq-diagnostics server_health_check 2>&1 | tail -5
echo "===== WHO TALKS TO DOCKER (docker sock users) ====="; echo "Tksc@197653" | sudo -S bash -c "ss -xn 2>/dev/null | grep docker.sock | head -5; lsof /var/run/docker.sock 2>/dev/null | awk "{print \$1, \$2}" | sort | uniq -c | sort -rn | head -10" 2>/dev/null
' 2>&1
