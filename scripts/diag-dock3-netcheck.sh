#!/usr/bin/env bash
# 网络层终验：ARP可达性 + 70秒定向抓包(机场->1883) + 最后OSD时刻，全部只读有界
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S -v 2>/dev/null
sudo bash <<'SUEOF'
echo "[START] $(date -u +%FT%T)"
echo "===[1] ARP 76.118==="
ip neigh show 192.168.76.118
ping -c 2 -W 1 192.168.76.118 >/dev/null 2>&1; ip neigh show 192.168.76.118
echo "===[2] route to 76.118==="
ip route get 192.168.76.118
echo "===[3] tcpdump 70s: any packets 76.118<->1883==="
timeout 70 tcpdump -ni any host 192.168.76.118 and port 1883 -c 40 2>&1 | tail -20
echo "[tcpdump end] $(date -u +%T)"
echo "===[4] last OSD timestamp 24h (bounded)==="
docker logs --since 24h tas-service 2>&1 | grep -a device_osd | grep -a 8UUXN2K00A0144 | grep -aoE "@timestamp[^,]+" | tail -1
echo "===[5] current emqx/redis==="
docker exec emqx emqx_ctl clients list 2>/dev/null | grep -a 8UUX | head -2
docker exec redis redis-cli GET "common_310_device:topo:8UUXN2K00A0144" | grep -aoE '"online_status":(true|false)' | tail -1
echo "[DONE] $(date -u +%FT%T)"
SUEOF
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
