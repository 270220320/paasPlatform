#!/usr/bin/env bash
# 2026-09-21 IP 切换修复2c：全业务库 dump 后 grep 76.141 残留定位
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
DBS="ApolloConfigDB ApolloPortalDB es310_management_service es_duplex_service live_service ThingAccessService es_open_service pf_live_service pf_device_service"
echo "===== 1. DUMP all business dbs ====="
sudo docker exec mysql_instance_1 sh -c "mysqldump -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --databases $DBS --skip-extended-insert --single-transaction 2>/dev/null" > /tmp/ip_dump.sql
wc -l /tmp/ip_dump.sql
echo "===== 2. COUNT 76.141 ====="
grep -c "192\.168\.76\.141" /tmp/ip_dump.sql || echo "0 - CLEAN"
echo "===== 3. CONTEXT lines (head 40) ====="
grep -n "192\.168\.76\.141" /tmp/ip_dump.sql | head -40
echo "===== FIX2c DONE ====="
REMOTE
