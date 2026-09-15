#!/usr/bin/env bash
# 修复执行（七）：airport_debug_mode_on → drone_open → 轮询验证
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
POST() { curl -s -m 90 -X POST 'http://127.0.0.1:38081/thing-access-service/inner/tas/devices/8UUXN2K00A0144/services' -H 'Content-Type: application/json' -d "{\"bid\":\"\",\"data\":$2,\"identifier\":\"$1\"}"; }
echo '===A_DEBUG_MODE_ON===';
date -u;
POST airport_debug_mode_on '{}' | head -c 500;
echo '';
sleep 4;
echo '===B_DRONE_OPEN===';
date -u;
POST drone_open '{}' | head -c 500;
echo '';
echo '===C_POLL===';
for i in 1 2 3 4 5 6 7 8; do
  sleep 15;
  echo "--- poll $i ($(date -u +%H:%M:%S)) ---";
  D=$(S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT sn,type,online_status FROM ThingAccessService.devices'" 2>/dev/null);
  T=$(S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT COUNT(*) FROM ThingAccessService.device_topos'" 2>/dev/null);
  echo "devices:[$D] topos_count:[$T]";
  echo "$D" | grep -q 1581F8 && { echo '*** DRONE REGISTERED ***'; break; };
done;
echo '===D_REPLIES===';
SE docker logs tas-service --since 3m 2>&1 | grep -a 'services_reply' | grep -ao '"method":"[^"]*"\|"result":[0-9-]*\|"status":"[a-z]*"' | paste - - - 2>/dev/null | tail -10;
echo '===E_ONLINE_MSG===';
SE docker logs tas-service --since 3m 2>&1 | grep -a 'online Message' | cut -c1-450 | tail -5;
echo '===F_EVENTS_DEBUG===';
SE docker logs tas-service --since 3m 2>&1 | grep -a '"topic":"thing/product/8UUXN2K00A0144/events"' | grep -ao '"method":"[^"]*"\|reason[^,]*\|"result":[0-9-]*' | tail -15;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
