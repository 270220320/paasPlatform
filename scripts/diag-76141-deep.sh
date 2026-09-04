#!/usr/bin/env bash
# 诊断 76.141：56789 端口归属 + rabbitmq 高CPU原因 + load 来源
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
echo "===== WHO MAPS 56789 ====="; echo "Tksc@197653" | sudo -S docker ps --format "{{.Names}} -> {{.Ports}}" 2>/dev/null | grep -E "56789|es-management"
echo "===== ALL DOCKER PORTS (grep mgmt/admin) ====="; echo "Tksc@197653" | sudo -S docker ps --format "{{.Names}} -> {{.Ports}}" 2>/dev/null | grep -iE "es-management|ts-admin" | head -5
echo "===== RABBITMQ QUEUES ====="; echo "Tksc@197653" | sudo -S docker exec rabbitmq rabbitmqctl list_queues name messages consumers 2>/dev/null | head -25
echo "===== RABBITMQ CONNECTIONS ====="; echo "Tksc@197653" | sudo -S docker exec rabbitmq rabbitmqctl list_connections user peer_host state 2>/dev/null | head -15
echo "===== VMSTAT 2s ====="; vmstat 1 3
echo "===== DOCKERD FD ====="; echo "Tksc@197653" | sudo -S ls /proc/1266/fd 2>/dev/null | wc -l
echo "===== DOCKER EVENTS ERR ====="; echo "Tksc@197653" | sudo -S journalctl -u docker --since "1 hour ago" -p err --no-pager 2>/dev/null | tail -10
' 2>&1
