#!/usr/bin/env bash
# 诊断（十三）：验证 Redis 中 flexnet-gw-host 键的实际值 + multipleIpSnMap 解析逻辑
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===REDIS_FLEXNET_KEYS===;
docker exec redis redis-cli -n 0 --scan --pattern "*flexnet*" | head -20;
echo ===REDIS_GWHOST_KEYS===;
docker exec redis redis-cli -n 0 --scan --pattern "*gw-host*" | head -20;
echo ===REDIS_SN_KEYS===;
docker exec redis redis-cli -n 0 --scan --pattern "*8UUXN2K00A0144*" | head -20;
echo ===GET_VALUES_WITH_TTL===;
for K in $(docker exec redis redis-cli -n 0 --scan --pattern "*flexnet*" ; docker exec redis redis-cli -n 0 --scan --pattern "*gw-host*"); do
  echo "KEY=$K TTL=$(docker exec redis redis-cli -n 0 TTL "$K") VALUE=[$(docker exec redis redis-cli -n 0 GET "$K")]";
done;
echo ===SRC_KEY_FORMAT===;
grep -rn "StoreDeviceHost\|getDeviceHostFromRedis\|flexnet-gw-host\|gw-host" /opt/cloudApi2_3/install/traefik/plugin/flexnet/*.go | head -30;
echo ===SRC_MULTIPLE_IP_PARSE===;
grep -rn -A 12 "multipleIpSnMap\|MultipleIpSnMap" /opt/cloudApi2_3/install/traefik/plugin/flexnet/*.go | head -60;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
