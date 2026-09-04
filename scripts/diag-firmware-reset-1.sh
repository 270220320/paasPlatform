#!/usr/bin/env bash
# 诊断固件上传 ERR_CONNECTION_RESET：MinIO 暴露端口/协议匹配/Traefik 路由/FlexNet 键
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===DOCKER_PS_MINIO_TRAEFIK_REDIS===;
docker ps --format "{{.Names}}\t{{.Ports}}" | grep -iE "minio|traefik|redis";
echo ===LISTEN_PORTS_9000_9001_443===;
ss -tlnp | grep -E ":(9000|9001|443|30812)[[:space:]]";
echo ===MINIO_HTTP_HEALTH_ON_9000===;
curl -sS --max-time 5 -o /dev/null -w "http_code=%{http_code}\n" http://127.0.0.1:9000/minio/health/live || echo "http_probe_fail_exit=$?";
echo ===HTTPS_CLIENTHELLO_TO_9000===;
curl -sk --max-time 5 -o /dev/null -w "http_code=%{http_code}\n" https://127.0.0.1:9000/ ; echo "https_probe_exit=$?";
echo ===TRAEFIK_CONF_MINIO_ROUTES===;
grep -rn -iE "minio|:9000" /opt/cloudApi2_3/install/conf/middleware-service/traefik/ 2>/dev/null | head -50;
echo ===REDIS_FLEXNET_KEYS===;
for c in $(docker ps --format "{{.Names}}" | grep -i redis); do echo "[$c]"; docker exec $c redis-cli --scan --pattern "flexnet*"; done;
echo ===TRAEFIK_RECENT_ERRORS===;
docker logs traefik --since 60m 2>&1 | grep -aiE "error|reset|timeout|413|body|client disconnect" | tail -30;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
