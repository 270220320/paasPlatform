#!/usr/bin/env bash
# 二代机场76.121第三轮：对照Dock3历史成功密码 + Dock2历史轨迹 + 部署侧密码配置
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S -v 2>/dev/null
sudo bash <<'SUEOF'
echo "[START] $(date -u +%FT%T)"
echo "===[1] all containers==="
docker ps --format '{{.Names}}\t{{.Status}}' | sort
echo "===[2] Dock3 8UUX connhook history (status + pwd used)==="
timeout 120 docker logs --since 240h tas-emqx-service 2>&1 | grep -a 8UUXN2K00A0144 | grep -aoE '"status":[0-9]+|"password":"[^"]*"' | paste - - 2>/dev/null | sort | uniq -c | sort -rn | head -8
echo "===[3] Dock3 8UUX last success 200 timestamp==="
timeout 120 docker logs --since 240h tas-emqx-service 2>&1 | grep -a 8UUXN2K00A0144 | grep -a '"status":200' | grep -aoE '"@timestamp":"[^"]*"' | tail -2
echo "===[4] Dock2 7CTDM history: any 200? which pwd?==="
timeout 120 docker logs --since 240h tas-emqx-service 2>&1 | grep -a 7CTDM6F00BQY6R | grep -aoE '"status":[0-9]+' | sort | uniq -c
echo "===[5] Dock2 7CTDM first/last seen==="
timeout 120 docker logs --since 240h tas-emqx-service 2>&1 | grep -a 7CTDM6F00BQY6R | grep -aoE '"@timestamp":"[^"]*"' | sed -n '1p;$p'
echo "===[6] deploy conf dirs==="
ls /opt/cloudApi2_3/install/conf 2>/dev/null | head -20
echo "===[7] device mqtt pwd config search==="
grep -rniE 'pwd|password|secret' /opt/cloudApi2_3/install/conf --include='*.yml' --include='*.yaml' --include='*.env' --include='*.properties' 2>/dev/null | grep -viE 'postgres|redis|emqx.*dashboard' | head -15
echo "[DONE] $(date -u +%FT%T)"
SUEOF
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
