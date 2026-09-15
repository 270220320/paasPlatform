#!/usr/bin/env bash
# 219015 修复验证（一）：绑定状态 + 无人机注册 + 拓扑建立 + 绑定确认报文
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
MY() { echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "$1" 2>/dev/null; }
echo '===BIND_STATUS_ES310===';
MY 'SELECT sn,device_bind_status,device_online_status,active_prj_uuid,updated_at FROM es310_management_service.devices';
echo '===ORG_DEVICES===';
MY 'SELECT * FROM es310_management_service.org_devices';
echo '===PRJ_DEVICES===';
MY 'SELECT * FROM es310_management_service.prj_devices';
echo '===TAS_DEVICES_ALL===';
MY 'SELECT sn,type,online_status,updated_at FROM ThingAccessService.devices';
echo '===TAS_TOPOS===';
MY 'SELECT sn,parent_device_sn,parent_index,status,updated_at FROM ThingAccessService.device_topos';
echo '===TOPO_JSON_ES310===';
MY 'SELECT device_topo FROM es310_management_service.devices WHERE sn="8UUXN2K00A0144"';
echo '===AIRPORT_BIND_REPLY===';
SE docker logs tas-service --since 6h 2>&1 | grep -a 'airport_bind_status' | grep -a 'requests_reply\|is_device_bind_organization' | tail -4 | cut -c1-700;
echo '===TASK_TYPE_ERR_RECENT===';
SE docker logs es-task --since 6h 2>&1 | grep -ac 'task type unsupported';
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
