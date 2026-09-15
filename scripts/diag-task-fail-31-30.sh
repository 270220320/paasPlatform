#!/usr/bin/env bash
# 219015 排查（十五）：tas 设备表/拓扑表取证（修正引号转义）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
MY() { echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "$1" 2>/dev/null; }
echo '===TAS_DEVICES_SCHEMA===';
MY 'DESCRIBE ThingAccessService.devices' | head -40;
echo '===TAS_DEVICE_ROW===';
MY 'SELECT * FROM ThingAccessService.devices WHERE sn="8UUXN2K00A0144" \G' | head -50;
echo '===TAS_TOPOS_SCHEMA===';
MY 'DESCRIBE ThingAccessService.device_topos' | head -25;
echo '===TAS_TOPO_ROWS===';
MY 'SELECT * FROM ThingAccessService.device_topos \G' | head -80;
echo '===TAS_DEVICE_MODELS===';
MY 'SELECT * FROM ThingAccessService.device_models \G' | head -60;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
