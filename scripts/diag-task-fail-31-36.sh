#!/usr/bin/env bash
# 219015 排查（二十一）：es-management/devops-be 绑定路由 + 组织/用户概览
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
echo '===ES_MGMT_MAIN_BIND_ROUTES===';
S docker exec es-management sh -c "tr -c '[:print:]' '\n' < /app/main" | grep -aoE '/(api|manage|inner)[a-z0-9_/.{}-]{2,60}(bind|device)[a-z0-9_/.{}-]{0,40}' | grep -avE 'github|aws|gin' | sort -u | head -40;
echo '===ES_MGMT_MAIN_AIRPORT===';
S docker exec es-management sh -c "tr -c '[:print:]' '\n' < /app/main" | grep -aoE '[a-z0-9_/.-]{0,40}airport[a-z0-9_/.-]{0,40}' | grep -avE 'github|aws' | sort -u | head -25;
echo '===DEVOPS_BE_LS===';
SE docker exec es310-privatization-devops-be sh -c 'ls -F /app 2>/dev/null' | head -12;
echo '===DEVOPS_BE_BIND===';
DBIN=$(S docker exec es310-privatization-devops-be sh -c 'for f in /app/*; do [ -f "$f" ] && [ $(wc -c <"$f") -gt 10000000 ] && echo "$f"; done' | head -2);
echo "bigbins: $DBIN";
for B in $DBIN; do
  echo "--scan $B";
  S docker exec es310-privatization-devops-be sh -c "tr -c '[:print:]' '\n' < $B" | grep -aoE '/(api|manage)[a-z0-9_/.{}-]{2,60}(bind|device)[a-z0-9_/.{}-]{0,40}' | grep -avE 'github|aws|gin' | sort -u | head -30;
done;
echo '===ORG_AND_USERS===';
SE docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "SELECT uuid,name FROM es310_management_service.organizations LIMIT 3; SELECT id,username,nickname FROM es310_management_service.user_accounts LIMIT 5" 2>/dev/null;
echo '===ES_MGMT_BIN_DEV_BIND===';
S docker exec es-management sh -c "tr -c '[:print:]' '\n' < /app/main" | grep -aoE '(BindDevice|BindAirport|DeviceBind|AirportBind)[A-Za-z]{0,30}' | sort -u | head -15;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
