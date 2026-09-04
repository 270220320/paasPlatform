#!/usr/bin/env bash
# 修复（一）：确认 DTCP 实际 SN + Redis 写入正确映射（立即生效）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===REDIS_MISS_LOGS===;
docker logs traefik --since 72h 2>&1 | grep -a "redis miss" | tail -5;
echo ===PARSE_SNIP_SRC===;
grep -rn -A 20 "func ParseSNIP" /opt/cloudApi2_3/install/traefik/plugin/flexnet/*.go | head -30;
echo ===WRITE_REDIS===;
docker exec redis redis-cli -n 0 SET flexnet-gw-host-8UUXN2K00A0144 192.168.76.141;
docker exec redis redis-cli -n 0 SET flexnet-gw-host-1581F8HGX252500A009X 192.168.76.141;
echo ===VERIFY===;
docker exec redis redis-cli -n 0 GET flexnet-gw-host-8UUXN2K00A0144;
docker exec redis redis-cli -n 0 GET flexnet-gw-host-1581F8HGX252500A009X;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
