#!/usr/bin/env bash
# 修复验证（十三）：最新失败任务全链路取证
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_NOW_AND_LAST_EXCEPTIONS===';
date -u;
SE docker logs es-task --since 30m 2>&1 | grep -a 'publish exceptions\|task end log\|recordSendExecuteFail\|GetTaskDevices\|get dock topo\|UpdateTaskStatus error' | cut -c1-420 | tail -10;
echo '===B_LAST_TASK_TRACE===';
TR=$(SE docker logs es-task --since 30m 2>&1 | grep -a 'task_uuid' | grep -ao '"task_uuid":"[a-f0-9-]*"' | tail -1 | cut -d'"' -f4);
echo "last_task_uuid: $TR";
SE docker logs es-task --since 30m 2>&1 | grep -a "$TR" | grep -avE 'UPDATE|SELECT|INSERT|relay' | grep -aiE 'error|warn|SendTaskExecute|GetTaskDevices|topo|prepare|execute|flighttask' | cut -c1-450 | tail -20;
echo '===C_TOPOS_LIST_NOW===';
curl -s -m 10 -X POST 'http://127.0.0.1:38081/thing-access-service/inner/tas/topos/list' -H 'Content-Type: application/json' -d '{"sns":["8UUXN2K00A0144"]}' | head -c 900;
echo '';
echo '===D_DRONE_STILL_ONLINE===';
SE docker logs tas-service --since 5m 2>&1 | grep -ac 'thing/product/1581F8HGX252500A009X';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT sn,online_status FROM ThingAccessService.devices'" 2>/dev/null;
echo '===E_TAS_LAST_10M_FLIGHTTASK===';
SE docker logs tas-service --since 10m 2>&1 | grep -a 'flighttask\|services' | grep -av 'osd' | cut -c1-400 | tail -10;
echo '===F_PUT_STATUS_RECENT===';
SE docker logs es-task --since 30m 2>&1 | grep -a 'flight-tasks' | grep -a 'PUT' | grep -ao '"time":"[^"]*"\|code:[0-9]*' | tail -8;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
