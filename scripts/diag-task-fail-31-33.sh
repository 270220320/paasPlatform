#!/usr/bin/env bash
# 219015 排查（十八）：绑定状态语义 + 无人机子设备 + topo_update 报文核查
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
MY() { echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "$1" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===ES310_DEVICES_SCHEMA===';
MY 'DESCRIBE es310_management_service.devices';
echo '===DRONE_ROWS_ALL_DBS===';
MY 'SELECT "es310",sn,device_model_key,status FROM es310_management_service.devices WHERE sn="1581F8HGX252500A009X"';
MY 'SELECT "pf",sn,device_model_key,status FROM pf_device_service.devices WHERE sn="1581F8HGX252500A009X"';
MY 'SELECT "tas",sn,type FROM ThingAccessService.devices';
echo '===PRJ_DEVICES===';
MY 'DESCRIBE es310_management_service.prj_devices' | head -15;
MY 'SELECT * FROM es310_management_service.prj_devices \G' | head -40;
echo '===TAS_TOPOUPDATE_SEARCH===';
SE docker logs tas-service --since 12h 2>&1 | grep -aiE 'topo_update|topoUpdate|topology' | head -8 | cut -c1-600;
echo '===TAS_BIND_SEARCH===';
SE docker logs tas-service --since 12h 2>&1 | grep -a '8UUXN2K00A0144' | grep -aiE 'bind|airport' | head -8 | cut -c1-600;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
