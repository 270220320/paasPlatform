#!/usr/bin/env bash
# 219015 排查（十九）：PG 全库设备状态 + 绑定 API 路由挖掘
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===PG_ALL_DEVICES===';
for db in pf_device_service es_task es_task_scheduler es_drc_service pf_control_core; do
  HAS=$(S docker exec postgres_instance_1 psql -U app_rw -d "$db" -Atc "select count(*) from information_schema.tables where table_name='devices'" 2>/dev/null);
  if [ "$HAS" != "0" ] && [ -n "$HAS" ]; then
    echo "==[pg/$db.devices]==";
    S docker exec postgres_instance_1 psql -U app_rw -d "$db" -c "select sn,device_bind_status,device_online_status,active_prj_uuid,device_model_key from devices where sn in ('8UUXN2K00A0144','1581F8HGX252500A009X')" 2>&1 | head -10;
  fi;
done;
echo '===PF_DEV_BIN_BIND_ROUTES===';
BINP=$(S docker exec pf-device-service sh -c 'ls /app 2>/dev/null | head; readlink -f /proc/1/exe');
echo "pfds pid1=$BINP";
for B in $(S docker exec pf-device-service sh -c 'ls -F /app | grep -av "/$"' 2>/dev/null | head -5); do
  echo "--scan /app/$B";
  S docker exec pf-device-service sh -c "tr -c '[:print:]' '\n' < /app/$B" | grep -aoE '/[a-z0-9_/.-]{4,60}(bind|binding)[a-z0-9_/.-]{0,40}' | sort -u | head -25;
done;
echo '===ES_MGMT_BIN_BIND_ROUTES===';
BINE=$(S docker exec es-management sh -c 'readlink -f /proc/1/exe');
echo "esm pid1=$BINE";
S docker exec es-management sh -c "tr -c '[:print:]' '\n' < $BINE" | grep -aoE '/[a-z0-9_/.-]{4,60}(bind|binding)[a-z0-9_/.-]{0,40}' | sort -u | head -25;
echo '===ES310_ORGS===';
MPASS='fzNi6gnMEM98z3VwmeLh0QHz';
SE docker exec mysql_instance_1 mysql -uroot -p$MPASS -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='es310_management_service' AND (table_name LIKE '%org%' OR table_name LIKE '%bind%' OR table_name LIKE '%user%' OR table_name LIKE '%account%')" 2>/dev/null;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
