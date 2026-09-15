#!/usr/bin/env bash
# 修复执行（二）：下发 drone_power_on + 验证无人机上线/topo 建立
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_SEND_DRONE_POWER_ON===';
TS=$(date +%s)000;
curl -s -m 30 -X POST 'http://127.0.0.1:38081/thing-access-service/inner/tas/devices/8UUXN2K00A0144/services' \
  -H 'Content-Type: application/json' \
  -d "{\"bid\":\"\",\"data\":{},\"identifier\":\"drone_power_on\"}" | head -c 600;
echo '';
echo '===B_WAIT_10S===';
sleep 10;
echo '===C_TAS_LOG_LAST_1M_SERVICES===';
SE docker logs tas-service --since 1m 2>&1 | grep -aiE 'services|power_on|reply' | grep -av 'osd\|state' | cut -c1-500 | head -12;
echo '===D_TAS_UPDATE_TOPO_AFTER===';
SE docker logs tas-service --since 2m 2>&1 | grep -a 'update_topo\|online Message\|SubDevices' | cut -c1-600 | head -8;
echo '===E_DRONE_STATUS_TOPIC===';
SE docker logs tas-service --since 2m 2>&1 | grep -a '"topic":"sys/product' | cut -c1-600 | head -6;
echo '===F_DEVICES_TOPOS_NOW===';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT sn,type,online_status FROM ThingAccessService.devices'" 2>/dev/null;
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT id,sn,parent_device_sn,status FROM ThingAccessService.device_topos'" 2>/dev/null;
echo '===G_EMQX_SERVICES_TRACE===';
SE docker logs tas-service --since 2m 2>&1 | grep -a 'thing/product/8UUXN2K00A0144/services' | cut -c1-600 | head -6;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
