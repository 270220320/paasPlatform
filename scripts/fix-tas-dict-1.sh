#!/usr/bin/env bash
# 修复执行（一）：修正 tas 字典 3-3-0 类别 rc→dock + 重启 tas 生效
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
MY() { echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "$1" 2>/dev/null; }
echo '===BEFORE===';
MY 'SELECT type,category,model FROM ThingAccessService.device_models WHERE type="3-3-0"';
echo '===DO_UPDATE===';
MY 'UPDATE ThingAccessService.device_models SET category="dock", updated_at=NOW() WHERE type="3-3-0"';
echo '===AFTER===';
MY 'SELECT type,category,model FROM ThingAccessService.device_models WHERE type="3-3-0"';
echo '===RESTART_TAS===';
S docker restart tas-service;
sleep 12;
echo '===TAS_UP===';
SE docker logs tas-service --since 1m 2>&1 | tail -3 | cut -c1-200;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
