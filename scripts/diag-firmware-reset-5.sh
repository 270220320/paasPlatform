#!/usr/bin/env bash
# 诊断（五）：env.map 配置 + frontend 静态资源真实路径
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===ENV_MAP_RELEVANT===;
grep -iE "minio|firmware|storage|endpoint|url|host|port|ip|oss|s3" /opt/cloudApi2_3/data/deploy/env.map | head -60;
echo ===FRONTEND_NGINX_CONF===;
docker exec frontend sh -c "cat /etc/nginx/conf.d/default.conf 2>/dev/null || cat /etc/nginx/nginx.conf 2>/dev/null" | grep -E "root|location|proxy_pass" | head -20;
echo ===FRONTEND_FIND_JS===;
docker exec frontend sh -c "find / -maxdepth 5 -name \"*.js\" -size +100k 2>/dev/null | head -10";
echo ===PAASJS_DEMO_DIR===;
docker exec frontend sh -c "find / -maxdepth 6 -type d -name \"paasjs-demo\" 2>/dev/null; find / -maxdepth 5 -type d -name \"device-manager-sdk*\" 2>/dev/null" | head -10;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
