#!/usr/bin/env bash
# 修复执行（五）：用 method 列查全部指令白名单
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
echo '===A_ALL_METHODS_252===';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT method,tag FROM ThingAccessService.commands ORDER BY tag,method'" 2>/dev/null;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
