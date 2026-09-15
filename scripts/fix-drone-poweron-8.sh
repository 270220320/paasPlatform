#!/usr/bin/env bash
# 修复执行（八）：debug_mode_on（等足45s）→ drone_open → 轮询
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
POST() { curl -s -m 100 -X POST 'http://127.0.0.1:38081/thing-access-service/inner/tas/devices/8UUXN2K00A0144/services' -H 'Content-Type: application/json' -d "{\"bid\":\"\",\"data\":$2,\"identifier\":\"$1\"}"; }
echo '===A_DEBUG_MODE_ON===';
date -u +%H:%M:%S;
POST airport_debug_mode_on '{}' | head -c 400;
echo '';
echo '===B_WAIT45_COLLECT===';
sleep 45;
SE docker logs tas-service --since 1m 2>&1 | grep -a 'services_reply\|device_debug\|events' | grep -av 'osd' | grep -ao '"method":"[^"]*"\|"result":[0-9-]*\|"status":"[a-z]*"\|"topic":"[^"]*"' | tail -20;
echo '===C_DRONE_OPEN===';
date -u +%H:%M:%S;
POST drone_open '{}' | head -c 400;
echo '';
echo '===D_POLL===';
for i in 1 2 3 4 5 6 7 8 9 10; do
  sleep 15;
  D=$(S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT sn,type,online_status FROM ThingAccessService.devices'" 2>/dev/null);
  T=$(S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT COUNT(*) FROM ThingAccessService.device_topos'" 2>/dev/null);
  echo "poll$i ($(date -u +%H:%M:%S)) devices:[$D] topos:[$T]";
  echo "$D" | grep -q 1581F8 && { echo '*** DRONE REGISTERED ***'; break; };
done;
echo '===E_LAST_REPLIES_AND_EVENTS===';
SE docker logs tas-service --since 5m 2>&1 | grep -aE 'services_reply|"topic":"thing/product/8UUXN2K00A0144/events"' | grep -aoE '"method":"[a-z_]*"|"result":-?[0-9]*|"status":"[a-z]*"|"output":\{[^}]*\}' | tail -30;
echo '===F_UPDATE_TOPO===';
SE docker logs tas-service --since 5m 2>&1 | grep -a 'update_topo' | grep -ao '"sub_devices":\[[^]]*\]' | tail -3;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
