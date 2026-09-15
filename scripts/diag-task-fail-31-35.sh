#!/usr/bin/env bash
# 219015 排查（二十）：pf-device server 二进制路由 + PG 错误显式 + 组织/用户数据 + 前端 JS 接口
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
echo '===PG_PFDS_ERR===';
SE docker exec postgres_instance_1 psql -U app_rw -d pf_device_service -c "select sn,device_bind_status,device_online_status,active_prj_uuid from devices" 2>&1 | head -12;
echo '===PF_SERVER_BIND_ROUTES===';
S docker exec pf-device-service sh -c "tr -c '[:print:]' '\n' < /app/server" | grep -aoE '(PUT|POST|GET)?/?[a-z0-9_/.-]{3,50}(bind|binding)[a-z0-9_/.-]{0,40}' | sort -u | head -30;
echo '===PF_SERVER_AIRPORT===';
S docker exec pf-device-service sh -c "tr -c '[:print:]' '\n' < /app/server" | grep -aoE '/[a-z0-9_/.-]{3,50}airport[a-z0-9_/.-]{0,40}' | sort -u | head -20;
echo '===ES_MGMT_LS===';
SE docker exec es-management sh -c 'ls -F /app 2>/dev/null' | head -15;
echo '===MYSQL_ORGS===';
SE docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "SELECT COUNT(*) FROM es310_management_service.organizations; SELECT COUNT(*) FROM es310_management_service.org_devices; SELECT COUNT(*) FROM es310_management_service.user_accounts; SELECT COUNT(*) FROM es310_management_service.users" 2>/dev/null;
echo '===FRONT_JS_BIND===';
FJS=$(S docker exec frontend sh -c 'ls /usr/share/nginx/html 2>/dev/null || ls /app 2>/dev/null || ls /var/www 2>/dev/null' | head -3);
echo "front root: $FJS";
S docker exec frontend sh -c 'grep -raohE "/[a-z0-9_/-]{3,50}(bind|binding)[a-z0-9_/-]{0,30}" /usr/share/nginx/html/static/js/ 2>/dev/null | sort -u | head -20';
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
