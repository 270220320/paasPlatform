#!/usr/bin/env bash
# 修复验证（十）：tas topo 未建立根因——status 上线报文 / sub_devices / devices 表
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_TAS_SYS_STATUS_TOPICS_24H===';
SE docker logs tas-service --since 24h 2>&1 | grep -a 'sys/product' | grep -ao '"topic":"[^"]*"' | sort | uniq -c;
echo '===B_TAS_ALL_TOPICS_24H_SAMPLE===';
SE docker logs tas-service --since 24h 2>&1 | grep -ao '"topic":"[^"]*"' | sort | uniq -c | sort -rn | head -20;
echo '===C_TAS_SUBDEVICE_LINES_24H===';
SE docker logs tas-service --since 24h 2>&1 | grep -ai 'sub_device' | cut -c1-600 | tail -8;
echo '===D_TAS_TOPO_LINES_24H===';
SE docker logs tas-service --since 24h 2>&1 | grep -aiE 'topo' | grep -av '"topic"' | cut -c1-500 | tail -12;
echo '===E_TAS_ONLINE_OFFLINE_EVT_24H===';
SE docker logs tas-service --since 24h 2>&1 | grep -aiE 'device.*(online|offline)' | grep -av 'online_status' | cut -c1-400 | tail -12;
echo '===F_MYSQL_DEVICES_NOW===';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT sn,type,category,online,created_at,updated_at FROM ThingAccessService.devices' " 2>/dev/null;
echo '--- device_topos count ---';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT COUNT(*) FROM ThingAccessService.device_topos' " 2>/dev/null;
echo '--- device_topos desc ---';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SHOW COLUMNS FROM ThingAccessService.device_topos' " 2>/dev/null;
echo '===G_DRONE_SN_ANYWHERE_24H_TASLOG===';
SE docker logs tas-service --since 24h 2>&1 | grep -ac '1581F8HGX252500A009X';
echo '===H_EMQX_SESSION_SUB===';
S docker exec $(S docker ps --format '{{.Names}}' | grep -i emqx | head -1) sh -c 'emqx ctl clients list 2>/dev/null | head -5' 2>/dev/null | cut -c1-300;
echo '===I_ESTASK_GETDOCKTOPO_LOGIC===';
S docker exec es-task sh -c "tr -c '[:print:]' '\n' < /app/http_main 2>/dev/null | grep -aiE 'get dock topo|get drone err|GetTaskDevices|drone.*(topo|sn)|topo.*drone' | sort -u | head -20" 2>/dev/null;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
