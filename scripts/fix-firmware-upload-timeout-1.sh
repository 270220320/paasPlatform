#!/usr/bin/env bash
# 修复：traefik minio/minio-static 入口 readTimeout=0（Traefik v3 默认 60s 掐断大文件上传）+ 重建 traefik
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
C=/opt/cloudApi2_3/install/compose.yaml;
cp $C $C.bak.$(date +%s);
echo ===INSERT_TIMEOUTS===;
sed -i "/--entrypoints.minio.address=:30802/a\      - --entrypoints.minio.transport.respondingTimeouts.readTimeout=0\n      - --entrypoints.minio.transport.respondingTimeouts.writeTimeout=0" $C;
sed -i "/--entrypoints.minio-static.address=:30804/a\      - --entrypoints.minio-static.transport.respondingTimeouts.readTimeout=0\n      - --entrypoints.minio-static.transport.respondingTimeouts.writeTimeout=0" $C;
grep -n "respondingTimeouts" $C;
echo ===COMPOSE_PROJECT_INFO===;
docker inspect traefik --format "project={{index .Config.Labels \"com.docker.compose.project\"}} workdir={{index .Config.Labels \"com.docker.compose.project.working_dir\"}}";
echo ===RECREATE_TRAEFIK===;
cd /opt/cloudApi2_3/install;
docker compose up -d --no-deps traefik 2>&1 || docker-compose up -d --no-deps traefik 2>&1;
echo ===WAIT_AND_HEALTHCHECK===;
sleep 12;
docker ps --filter name=traefik --format "{{.Names}} {{.Status}}";
docker exec traefik traefik version 2>/dev/null | head -1;
curl -s -o /dev/null -w "30802_minio=%{http_code}\n" -m 8 http://127.0.0.1:30802/minio/health/live;
curl -s -o /dev/null -w "30812_backend=%{http_code}\n" -m 8 http://127.0.0.1:30812/;
echo ===VERIFY_ARGS===;
docker inspect traefik --format "{{json .Config.Cmd}}" | tr "," "\n" | grep -E "minio.*(address|Timeout)";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
