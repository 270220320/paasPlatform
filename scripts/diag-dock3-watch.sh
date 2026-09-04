#!/usr/bin/env bash
# 绑定观察实验：T0 基线快照（全部只读）+ 5 分钟纯只读轮询，每步带时间戳，到点自动退出
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S -v 2>/dev/null
sudo bash <<'SUEOF'
SN=8UUXN2K00A0144
echo "[T0_BASELINE] $(date -u +%FT%T.%3NZ)"
echo "[ACTION-1 read emqx client] $(date -u +%T)"
docker exec emqx emqx_ctl clients list 2>/dev/null | grep -a $SN | grep -aoE 'peername=[^,]+|connected=(true|false)|connected_at=[0-9]+'
echo "[ACTION-2 read redis topo] $(date -u +%T)"
docker exec redis redis-cli GET "common_310_device:topo:$SN"
echo "[ACTION-3 read mysql binding] $(date -u +%T)"
docker exec mysql_instance_1 mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -e "select sn,client_id,created_at,updated_at from ThingAccessService.device_app_bindings" 2>/dev/null
echo "[ACTION-4 read tas-emqx auth log 12h] $(date -u +%T)"
docker logs --since 12h tas-emqx-service 2>&1 | grep -aiE 'verifyAppid|password error|thingConnAuth' | tail -12
echo "[ACTION-5 count dock OSD 10m] $(date -u +%T)"
docker logs --since 10m tas-service 2>&1 | grep -a device_osd | grep -ac $SN
echo "[T0_DONE] $(date -u +%FT%T.%3NZ)"
echo "==== POLL_START 12x20s read-only ===="
for i in $(seq 1 12); do
  TS=$(date -u +%T)
  TOPO=$(docker exec redis redis-cli GET "common_310_device:topo:$SN" | grep -aoE '"online_status":(true|false)' | tail -1)
  EMQ=$(docker exec emqx emqx_ctl clients list 2>/dev/null | grep -a $SN | grep -aoE 'connected=(true|false)|connected_at=[0-9]+' | tr '\n' ' ')
  echo "[POLL $i] $TS dock_online=$TOPO emqx=$EMQ"
  [ $i -lt 12 ] && sleep 20
done
echo "[POLL_DONE] $(date -u +%FT%T.%3NZ)"
echo "[T1 final OSD count 5m] $(date -u +%T)"
docker logs --since 5m tas-service 2>&1 | grep -a device_osd | grep -ac $SN
SUEOF
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
