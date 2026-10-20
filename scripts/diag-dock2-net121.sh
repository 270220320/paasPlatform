#!/usr/bin/env bash
# 二代机场(Dock2) 192.168.76.121 绑定不上：网络层+服务层排查（只读有界）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S -v 2>/dev/null
sudo bash <<'SUEOF'
echo "[START] $(date -u +%FT%T)"
echo "===[1] ARP before==="
ip neigh show 192.168.76.121 || true
echo "===[2] ping 76.121 x4==="
ping -c 4 -W 1 192.168.76.121 2>&1 | tail -3
echo "===[3] ARP after==="
ip neigh show 192.168.76.121 || true
echo "===[4] route==="
ip route get 192.168.76.121
echo "===[5] current 1883 ESTABLISHED peers==="
ss -tn state established '( sport = :1883 )' 2>/dev/null | head -20
echo "===[6] tcpdump 40s: any packets from/to 76.121==="
timeout 40 tcpdump -ni any host 192.168.76.121 -c 60 2>&1 | tail -25
echo "===[7] docker core services==="
docker ps --format '{{.Names}}\t{{.Status}}' | grep -Ei 'emqx|redis|tas|gateway|nginx|traefik' | head -20
echo "===[8] emqx clients (truncated)==="
docker exec emqx emqx_ctl clients list 2>/dev/null | grep -aoE 'clientid=[^ ]+' | head -30
echo "===[9] emqx auth log 2h: 76.121 / auth fail==="
docker logs --since 2h tas-emqx-service 2>&1 | grep -aiE '76\.121|verifyAppid|auth.*(fail|deny|error)' | tail -20
echo "[DONE] $(date -u +%FT%T)"
SUEOF
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
