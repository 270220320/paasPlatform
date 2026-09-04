#!/usr/bin/env bash
# 诊断（十四）：确认 redis 容器拓扑 + FlexNet 实连的 redis + clientHostFromRequest 回退逻辑
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===ALL_REDIS_CONTAINERS===;
docker ps -a --format "{{.Names}} | {{.Image}} | {{.Networks}} | {{.Status}}" | grep -ai redis;
echo ===REDIS_IPS===;
for C in $(docker ps --format "{{.Names}}" | grep -ai redis); do
  echo "$C -> $(docker inspect $C --format "{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}")";
done;
echo ===TRAEFIK_RESOLVES_REDIS===;
docker exec traefik sh -c "getent hosts redis 2>/dev/null || nslookup redis 2>/dev/null || cat /etc/hosts" 2>&1 | head -5;
echo ===HANDLER_DTCP_FULL===;
cat /opt/cloudApi2_3/install/traefik/plugin/flexnet/handler_dtcp.go;
echo ===CLIENT_HOST_FUNC===;
grep -rn -B 3 -A 15 "func clientHostFromRequest\|func.*clientHost" /opt/cloudApi2_3/install/traefik/plugin/flexnet/*.go | head -50;
echo ===GETDEVICEHOST_FUNC===;
sed -n "200,245p" /opt/cloudApi2_3/install/traefik/plugin/flexnet/flexnet.go;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
