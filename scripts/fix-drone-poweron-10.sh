#!/usr/bin/env bash
# 修复执行（十）：等无人机完全上线 → 验证 topo nodes → 退出调试模式
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
POST() { curl -s -m 100 -X POST 'http://127.0.0.1:38081/thing-access-service/inner/tas/devices/8UUXN2K00A0144/services' -H 'Content-Type: application/json' -d "{\"bid\":\"\",\"data\":$2,\"identifier\":\"$1\"}"; }
echo '===A_WAIT_ONLINE===';
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  sleep 15;
  D=$(S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT sn,online_status FROM ThingAccessService.devices'" 2>/dev/null | tr '\n' ' ');
  T=$(S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT COUNT(*) FROM ThingAccessService.device_topos'" 2>/dev/null);
  echo "poll$i ($(date -u +%H:%M:%S)) devices:[$D] topos:[$T]";
  echo "$D" | grep -q '1581F8HGX252500A009X 1' && { echo '*** DRONE ONLINE ***'; break; };
done;
echo '===B_TOPOS_LIST_NOW===';
curl -s -m 10 -X POST 'http://127.0.0.1:38081/thing-access-service/inner/tas/topos/list' -H 'Content-Type: application/json' -d '{"sns":["8UUXN2K00A0144"]}' | head -c 900;
echo '';
echo '===C_JOB_PROGRESS_EVENTS===';
SE docker logs tas-service --since 4m 2>&1 | grep -a '"topic":"thing/product/8UUXN2K00A0144/events"' | grep -a 'drone_open\|device_debug' | tail -3 | cut -c1-450;
echo '===D_DRONE_OSD_CHECK===';
SE docker logs tas-service --since 2m 2>&1 | grep -a 'thing/product/1581F8HGX252500A009X' | tail -2 | grep -ao '"topic":"[^"]*"' | sort | uniq -c;
echo '===E_DEBUG_MODE_CLOSE===';
date -u +%H:%M:%S;
POST debug_mode_close '{}' | head -c 400;
echo '';
sleep 3;
echo '===F_TOPOS_AFTER_CLOSE===';
curl -s -m 10 -X POST 'http://127.0.0.1:38081/thing-access-service/inner/tas/topos/list' -H 'Content-Type: application/json' -d '{"sns":["8UUXN2K00A0144"]}' | head -c 900;
echo '';
echo '===G_DEVICES_FINAL===';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT sn,type,online_status FROM ThingAccessService.devices'" 2>/dev/null;
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT id,sn,parent_device_sn,status FROM ThingAccessService.device_topos'" 2>/dev/null;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
