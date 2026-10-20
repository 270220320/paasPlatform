#!/usr/bin/env bash
# 二代机场76.121第二轮：抓CONNECT明文取username/SN + EMQX认证日志 + Dock3在线对照
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S -v 2>/dev/null
sudo bash <<'SUEOF'
echo "[START] $(date -u +%FT%T)"
echo "===[1] tcpdump -A 15s: CONNECT payload from 76.121==="
timeout 15 tcpdump -A -ni any 'host 192.168.76.121 and port 1883 and tcp[tcpflags] & 0x08 != 0' -c 6 2>&1 | head -80
echo "===[2] emqx clients raw head==="
docker exec emqx emqx_ctl clients list 2>/dev/null | head -12
echo "===[3] Dock3 8UUX online?==="
docker exec emqx emqx_ctl clients list 2>/dev/null | grep -a 8UUX | head -2
echo "===[4] emqx log 15m: authn/121==="
docker logs --since 15m emqx 2>&1 | grep -aiE 'authn|76\.121' | tail -15
echo "===[5] tas-emqx-service log 5m: username/thing/conn==="
docker logs --since 5m tas-emqx-service 2>&1 | grep -aiE 'username|thing/|conn.*info|client' | tail -15
echo "===[6] emqx authn config==="
V=/var/lib/docker/volumes/e5b48cee9adb6eca1a8ea8a05e02529a2b82ed14233acbee658112b21b7472fa/_data
grep -riE 'authn|authentication|http' $V/../ 2>/dev/null | grep -aiE 'emqx.*conf|authn' | head -5
find /opt/cloudApi2_3 -name '*.conf' 2>/dev/null | head -10
echo "[DONE] $(date -u +%FT%T)"
SUEOF
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
