#!/usr/bin/env bash
# 219015 排查（二十六）：es-management 路由 URL + 绑定请求历史 + 用户账号体系
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
echo '===ESMGMT_URL_ROUTES===';
S docker exec es-management sh -c "tr -c '[:print:]' '\n' < /app/main" | grep -aoE '/(manage|inner|api)/[a-z0-9_{}/.-]{3,70}' | grep -aiE 'bind|device' | sort -u | head -40;
echo '===ESMGMT_BIND_REQ_HISTORY===';
SE docker logs es-management --since 24h 2>&1 | grep -aiE 'bind' | grep -av 'gin/binding' | tail -20 | cut -c1-500;
echo '===ESMGMT_SN_HISTORY===';
SE docker logs es-management --since 24h 2>&1 | grep -a '8UUXN2K00A0144' | tail -10 | cut -c1-400;
echo '===USER_ACCOUNTS_SCHEMA===';
SE docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "DESCRIBE es310_management_service.user_accounts" 2>/dev/null | head -20;
echo '===USERS_SAMPLE===';
SE docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "SELECT id,user_type,status FROM es310_management_service.user_accounts LIMIT 5" 2>/dev/null;
echo '===TAS_BIND_CONFIG_SYMBOL===';
S docker exec tas-service sh -c 'BIN=$(ls /*.jar 2>/dev/null | head -1); ls /app 2>/dev/null | head -5' ;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
