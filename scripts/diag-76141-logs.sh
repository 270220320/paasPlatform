#!/usr/bin/env bash
# 诊断 76.141：es-management 重启原因 + rabbitmq 状态 + dockerd FD 构成
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
echo "===== ES-MANAGEMENT INSPECT (ports/restart) ====="; echo "Tksc@197653" | sudo -S docker inspect es-management --format "RestartCount={{.RestartCount}} StartedAt={{.State.StartedAt}} Status={{.State.Status}} Ports={{.HostConfig.PortBindings}}" 2>/dev/null
echo "===== ES-MANAGEMENT LAST LOGS ====="; echo "Tksc@197653" | sudo -S docker logs es-management --tail 20 2>&1 | tail -20
echo "===== RABBITMQ OVERVIEW (mgmt API) ====="; echo "Tksc@197653" | sudo -S docker exec rabbitmq curl -s -u guest:guest -m 10 http://127.0.0.1:15672/api/overview 2>/dev/null | head -c 600; echo
echo "===== RABBITMQ QUEUE COUNT ====="; echo "Tksc@197653" | sudo -S docker exec rabbitmq curl -s -u guest:guest -m 10 http://127.0.0.1:15672/api/queues 2>/dev/null | python3 -c "import sys,json;qs=json.load(sys.stdin);print(\"queues:\",len(qs));[print(q[\"name\"][:60],q[\"messages\"],q[\"consumers\"]) for q in sorted(qs,key=lambda x:-x[\"messages\"])[:12]]" 2>&1 | head -15
echo "===== DOCKERD FD TYPES ====="; echo "Tksc@197653" | sudo -S bash -c "ls -l /proc/1266/fd 2>/dev/null | grep -oE \"(socket|pipe|anon_inode|/var/lib/docker[^(]*)\" | sort | uniq -c | sort -rn | head -8" 2>/dev/null
echo "===== RABBITMQ LOG TAIL ====="; echo "Tksc@197653" | sudo -S docker logs rabbitmq --tail 15 2>&1 | tail -15
' 2>&1
