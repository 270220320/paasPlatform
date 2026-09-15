#!/usr/bin/env bash
# 219015 排查（二十七）：前端访问入口 + 账号清单 + traefik 路由
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
echo '===FRONTEND_PORTS===';
S docker port frontend;
echo '===TRAEFIK_PORTS===';
S docker port traefik;
echo '===FRONTEND_CONF===';
SE docker exec frontend sh -c 'cat /etc/nginx/conf.d/default.conf 2>/dev/null | head -40' | head -45;
echo '===ACCOUNTS===';
SE docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "SELECT account,type,created_at FROM es310_management_service.user_accounts WHERE deleted_at IS NULL" 2>/dev/null;
echo '===USERS===';
SE docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "SELECT id,nickname,created_at FROM es310_management_service.users LIMIT 5" 2>/dev/null;
echo '===ORG_MEMBERS===';
SE docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "SELECT * FROM es310_management_service.org_members LIMIT 5" 2>/dev/null;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
