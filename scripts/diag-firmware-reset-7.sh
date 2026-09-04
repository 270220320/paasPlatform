#!/usr/bin/env bash
# 诊断（七）：traefik 版本/启动参数/entrypoint 配置来源（定位 60s readTimeout）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TRAEFIK_VERSION===;
docker exec traefik traefik version 2>/dev/null || docker inspect traefik --format "{{.Config.Image}}";
echo ===TRAEFIK_ARGS===;
docker inspect traefik --format "{{json .Config.Cmd}} {{json .Config.Entrypoint}}";
echo ===TRAEFIK_MOUNTS===;
docker inspect traefik --format "{{json .Mounts}}" | tr "{" "\n" | grep -E "Source|Destination" | head -20;
echo ===COMPOSE_TRAEFIK_SECTION===;
grep -n -A 60 "traefik:" /opt/cloudApi2_3/install/compose.yaml | head -100;
echo ===TRAEFIK_STATIC_FILE===;
for f in /opt/cloudApi2_3/install/conf/middleware-service/traefik/traefik.yml /opt/cloudApi2_3/install/conf/middleware-service/traefik/traefik.yaml /opt/cloudApi2_3/install/conf/middleware-service/traefik/traefik.toml; do [ -f $f ] && echo "--- $f" && cat $f; done;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
