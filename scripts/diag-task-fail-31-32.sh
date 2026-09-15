#!/usr/bin/env bash
# 219015 排查（十七）：Dock3 正确 model key 取证——字典全表 + 设备上线原始报文 + pf 库对照
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
MY() { echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "$1" 2>/dev/null; }
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===DM_ALL_KEYS===';
MY 'SELECT type,device_type,device_sub_type,category,model FROM ThingAccessService.device_models ORDER BY type';
echo '===DM_ENUMS_ES310===';
MY 'SELECT * FROM es310_management_service.device_model_enums LIMIT 40' | head -45;
echo '===PF_DEVICES_ROW===';
MY 'SELECT * FROM pf_device_service.devices WHERE sn="8UUXN2K00A0144" \G' | head -40;
echo '===TAS_ONLINE_ORIGIN===';
SE docker logs tas-service --since 12h 2>&1 | grep -a '8UUXN2K00A0144' | grep -aiE 'online|topo_update|state|model_key|sub_type|device_type' | head -15 | cut -c1-800;
echo '===EMQX_STATE_MSG===';
SE docker logs tas-service --since 12h 2>&1 | grep -a 'thing/product/8UUXN2K00A0144' | grep -avE 'services_reply|osd' | head -10 | cut -c1-800;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
