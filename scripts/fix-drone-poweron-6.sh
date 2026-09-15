#!/usr/bin/env bash
# 修复执行（六）：下发 drone_open（飞行器开机）+ 轮询验证 topo 建立
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_SEND_DRONE_OPEN===';
date -u;
curl -s -m 60 -X POST 'http://127.0.0.1:38081/thing-access-service/inner/tas/devices/8UUXN2K00A0144/services' \
  -H 'Content-Type: application/json' \
  -d '{"bid":"","data":{},"identifier":"drone_open"}' | head -c 800;
echo '';
echo '===B_POLL_LOOP===';
for i in 1 2 3 4 5 6; do
  sleep 15;
  echo "--- poll $i ($(date -u +%H:%M:%S)) ---";
  D=$(S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT sn,type,online_status FROM ThingAccessService.devices'" 2>/dev/null);
  T=$(S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT COUNT(*) FROM ThingAccessService.device_topos'" 2>/dev/null);
  echo "devices:[$D] topos_count:[$T]";
  TO=$(SE docker logs tas-service --since ${i}5s 2>&1 | grep -a 'update_topo' | tail -1 | grep -ao '"sub_devices":\[[^]]*\]' | head -c 300);
  [ -n "$TO" ] && echo "last_sub_devices: $TO";
  echo "$D" | grep -q 1581F8 && { echo '*** DRONE REGISTERED ***'; break; };
done;
echo '===C_PROGRESS_LOGS===';
SE docker logs tas-service --since 2m 2>&1 | grep -aiE 'drone_open|device_debug|progress|job' | grep -av 'osd' | cut -c1-450 | tail -12;
echo '===D_ONLINE_MSG_NOW===';
SE docker logs tas-service --since 3m 2>&1 | grep -a 'online Message' | cut -c1-500 | tail -4;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
