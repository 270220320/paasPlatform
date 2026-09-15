#!/usr/bin/env bash
# 司空3.1升级（十一）：排查 web hook url not valid —— 检查网关容器与 ISV_WEBHOOK_URL 配置
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===DOCKER_PS_NOW===';
echo 'Tksc@197653' | sudo -S docker ps --format "{{.Names}}\t{{.Status}}" 2>/dev/null | head -30;
echo "total=$(echo 'Tksc@197653' | sudo -S docker ps -q 2>/dev/null | wc -l)";
echo '===GATEWAY_WEBHOOK_CONFIG===';
echo 'Tksc@197653' | sudo -S docker exec pf-gateway-service sh -c 'grep -A2 -B2 webhook /app/config/config.tmpl.yaml 2>/dev/null || grep -ri webhook /app/config/ 2>/dev/null | head -10' 2>&1;
echo '===GATEWAY_ENV===';
echo 'Tksc@197653' | sudo -S docker exec pf-gateway-service sh -c 'env | grep -iE "webhook|isv" ' 2>&1;
echo '===NEW_INSTALL_DIR===';
echo 'Tksc@197653' | sudo -S find /opt/cloudApi2_3.1 -maxdepth 2 -name 'setuptools' -o -maxdepth 2 -name '.env' -o -maxdepth 2 -name 'env' 2>/dev/null;
ls /opt/cloudApi2_3.1/ 2>/dev/null;
echo '===INSTALL_DIR_STRUCTURE===';
find /opt/cloudApi2_3.1 -maxdepth 2 -type d 2>/dev/null | head -15;
echo '===COMPOSE_PROJECTS===';
echo 'Tksc@197653' | sudo -S docker compose ls 2>/dev/null;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
