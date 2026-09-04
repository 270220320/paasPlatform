#!/usr/bin/env bash
# 受控实验：验证"ping 唤醒机场"假说
# 阶段0 基线(只读) -> 阶段1 对照3分钟(不发任何包给机场) -> 阶段2 单次ping(唯一嫌疑动作) -> 阶段3 观察3分钟
# 若机场当前已在线则直接报告并退出
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S -v 2>/dev/null
sudo bash <<'SUEOF'
SN=8UUXN2K00A0144
ts(){ date -u +%H:%M:%S.%3N; }
echo "[P0_BASELINE] $(ts)"
ENTRY=$(docker exec emqx emqx_ctl clients list 2>/dev/null | grep -a $SN | head -1)
echo "emqx_entry: ${ENTRY:-<none>}"
echo "redis_dock_online: $(docker exec redis redis-cli GET common_310_device:topo:$SN | grep -aoE 'online_status.:(true|false)' | tail -1)"
echo "arp_before: $(ip neigh show 192.168.76.118)"
echo "last_osd_12h: $(docker logs --since 12h tas-service 2>&1 | grep -a device_osd | grep -a $SN | grep -aoE '@timestamp[^,]+' | tail -1)"
echo "auth_attempts_12h_count: $(docker logs --since 12h tas-emqx-service 2>&1 | grep -ac verifyAppid)"
docker logs --since 12h tas-emqx-service 2>&1 | grep -a verifyAppid | grep -aoE '@timestamp[^,]+' | tail -3
if [ -n "$ENTRY" ]; then
  echo "[ALREADY_ONLINE] $(ts) 机场已有MQTT连接，实验取消，仅输出基线"
  exit 0
fi
echo "[P1_CONTROL_START_3min_no_traffic_to_dock] $(ts)"
for i in 1 2 3 4 5 6 7 8 9; do
  sleep 20
  E=$(docker exec emqx emqx_ctl clients list 2>/dev/null | grep -ac $SN)
  echo "[C$i] $(ts) emqx_entry=$E"
done
echo "[P1_CONTROL_END] $(ts)"
echo "[P2_PING_TRIGGER] $(ts)"
ping -c 3 -W 1 192.168.76.118 2>&1 | tail -3
echo "[P2_PING_DONE] $(ts)"
echo "arp_after_ping: $(ip neigh show 192.168.76.118)"
echo "[P3_OBSERVE_START_3min] $(ts)"
for i in $(seq 1 18); do
  E=$(docker exec emqx emqx_ctl clients list 2>/dev/null | grep -a $SN | grep -aoE 'connected_at=[0-9]+' | head -1)
  R2=$(docker exec redis redis-cli GET common_310_device:topo:$SN | grep -aoE 'online_status.:(true|false)' | tail -1)
  echo "[O$i] $(ts) emqx=$E redis=$R2"
  sleep 10
done
echo "[P3_DONE] $(ts)"
SUEOF
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
