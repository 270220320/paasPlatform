#!/usr/bin/env bash
# 修复执行（三）：找正确的开机/调试指令 identifier（thing model 表 + 前端 JS 字典）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_TAS_MYSQL_ALL_TABLES===';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SHOW TABLES FROM ThingAccessService'" 2>/dev/null;
echo '===B_THINGMODEL_TABLES_CONTENT===';
for T in $(S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SHOW TABLES FROM ThingAccessService'" 2>/dev/null | grep -aiE 'model|service|thing|command'); do
  echo "--- $T ---";
  S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT COUNT(*) FROM ThingAccessService.\`$T\`'" 2>/dev/null;
done
echo '===C_FRONTEND_JS_LIST===';
curl -sk -m 6 https://127.0.0.1/ 2>/dev/null | grep -oE '(src|href)="[^"]*\.(js|css)[^"]*"' | head -20;
echo '===D_FRONTEND_STATIC_LS===';
S docker exec $(S docker ps --format '{{.Names}}' | grep -aiE '^(frontend|web|nginx)' | head -1) sh -c 'ls /usr/share/nginx/html/static/js 2>/dev/null | head -30' 2>/dev/null;
echo '===E_TRAEFIK_ROUTER_FRONTEND===';
S docker exec traefik sh -c 'wget -qO- http://127.0.0.1:8080/api/http/routers 2>/dev/null || cat /etc/traefik/traefik.yml 2>/dev/null || ls /etc/traefik' 2>/dev/null | head -c 1500;
echo '';
echo '===F_ES_BIN_SERVICE_IDS===';
S docker exec es-task sh -c "tr -c '[:print:]' '\n' < /app/http_main 2>/dev/null | grep -aE '^(drone_|cover_|charge_|device_|esd_|alarm_|battery_|airport_|data_format|sd r)' | sort -u | head -30" 2>/dev/null;
echo '===G_PF_BIN_SERVICE_IDS===';
S docker exec pf-control-core sh -c "tr -c '[:print:]' '\n' < \$(ls /app/* 2>/dev/null | head -1) 2>/dev/null | grep -aE 'drone_power|cover_open|debug_mode' | sort -u | head -10" 2>/dev/null || echo NO_PF;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
