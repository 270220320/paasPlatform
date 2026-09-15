#!/usr/bin/env bash
# 修复执行（四）：查 commands 白名单表找开机指令
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
echo '===A_COMMANDS_SCHEMA===';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SHOW COLUMNS FROM ThingAccessService.commands'" 2>/dev/null;
echo '===B_POWERON_LIKE_CMDS===';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -e 'SELECT * FROM ThingAccessService.commands WHERE identifier LIKE \"%power%\" OR identifier LIKE \"%open%\" OR identifier LIKE \"%cover%\" OR identifier LIKE \"%charge%\" OR identifier LIKE \"%reboot%\" OR identifier LIKE \"%escape%\" OR identifier LIKE \"%store%\" \G'" 2>/dev/null | head -80;
echo '===C_SAMPLE_ROWS===';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -e 'SELECT * FROM ThingAccessService.commands LIMIT 3 \G'" 2>/dev/null | head -50;
echo '===D_ALL_IDENTIFIERS_GREP_DOCK===';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT identifier FROM ThingAccessService.commands'" 2>/dev/null | sort -u | grep -iE 'drone|aircraft|fly|dock|机场|飞行器' | head -30;
echo '===E_FLIGHTTASK_CMDS_EXIST===';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT identifier FROM ThingAccessService.commands WHERE identifier LIKE \"%flighttask%\"'" 2>/dev/null;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
