#!/usr/bin/env bash
# 219015 排查（十六）：device_models 字典 3-3-0 映射 + 全部设备行 + pf/es 设备库对照
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
MY() { echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "$1" 2>/dev/null; }
echo '===DM_SCHEMA===';
MY 'DESCRIBE ThingAccessService.device_models';
echo '===DM_3_3_0===';
MY 'SELECT * FROM ThingAccessService.device_models WHERE model_key="3-3-0" \G';
echo '===DM_DOMAIN3_ALL===';
MY 'SELECT * FROM ThingAccessService.device_models WHERE model_key LIKE "3-%" \G' | head -60;
echo '===DM_DOCK_ALL===';
MY 'SELECT * FROM ThingAccessService.device_models WHERE model="dock" \G' | head -40;
echo '===TAS_DEVICES_ALL===';
MY 'SELECT * FROM ThingAccessService.devices \G' | head -40;
echo '===ES310_DEVICES_ROW===';
MY 'SELECT * FROM es310_management_service.devices WHERE sn="8UUXN2K00A0144" \G' | head -50;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
