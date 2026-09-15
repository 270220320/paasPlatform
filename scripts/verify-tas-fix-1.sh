#!/usr/bin/env bash
# 修复验证（七）：tas 重启后机场重连 + 拓扑/设备注册/绑定状态复查
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
MY() { echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "$1" 2>/dev/null; }
echo '===WAIT_RECONNECT===';
for i in $(seq 1 12); do
  if SE docker logs tas-service --since 3m 2>&1 | grep -aq 'BindDevice'; then echo "reconnected at iter $i"; break; fi
  sleep 10;
done;
echo '===TAS_TOPO_AFTER===';
SE docker logs tas-service --since 5m 2>&1 | grep -aiE 'BindDevice|topo|device_model' | grep -a '8UUXN2K00A0144' | tail -8 | cut -c1-500;
echo '===TAS_DEVICES_NOW===';
MY 'SELECT sn,type,online_status FROM ThingAccessService.devices';
echo '===TAS_TOPOS_NOW===';
MY 'SELECT sn,parent_device_sn,status FROM ThingAccessService.device_topos';
echo '===BIND_STATUS_NOW===';
MY 'SELECT sn,device_bind_status,active_prj_uuid FROM es310_management_service.devices';
MY 'SELECT COUNT(*) FROM es310_management_service.org_devices';
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
