#!/usr/bin/env bash
# 诊断（二）：Traefik entrypoint 协议 / MinIO 对外访问路径 / 后端存储 endpoint 配置
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===DYNAMIC_CONF_FULL===;
sed -n "1,150p" /opt/cloudApi2_3/install/conf/middleware-service/traefik/dynamic_conf.yaml;
echo ===TRAEFIK_DIR_LIST===;
ls -la /opt/cloudApi2_3/install/conf/middleware-service/traefik/;
echo ===TRAEFIK_STATIC_CONF===;
grep -rn -iE "entryPoint|address|tls" /opt/cloudApi2_3/install/conf/middleware-service/traefik/traefik.toml /opt/cloudApi2_3/install/conf/middleware-service/traefik/traefik.yml 2>/dev/null | head -40;
echo ===MINIO_ENV===;
docker inspect minio --format "{{json .Config.Env}}";
echo;
docker inspect minio-static --format "{{json .Config.Env}}";
echo ===LOCAL_HTTP_PROBE_30802===;
curl -sI --max-time 5 http://127.0.0.1:30802/minio/health/live | head -5;
echo "http_probe_exit=$?";
echo ===LOCAL_TLS_PROBE_30802===;
curl -sk --max-time 5 https://127.0.0.1:30802/ -o /dev/null -w "code=%{http_code}\n";
echo "tls_probe_exit=$?";
echo ===ALL_CONTAINERS===;
docker ps --format "{{.Names}}" | sort | tr "\n" " ";
echo;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
