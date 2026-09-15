#!/usr/bin/env bash
# 司空3.1升级（十二）：查看3.1 env文件与setuptools，确认ISV_WEBHOOK_URL配置方式
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===PKG_DIR_LIST===';
ls -la /opt/cloudApi2_3.1/fh2-privatization-install-packages/ | head -20;
echo '===ENV_FILE_CONTENT===';
cat /opt/cloudApi2_3.1/fh2-privatization-install-packages/env;
echo '===SETUPTOOLS_TYPE===';
file /opt/cloudApi2_3.1/fh2-privatization-install-packages/setuptools 2>/dev/null;
head -30 /opt/cloudApi2_3.1/fh2-privatization-install-packages/setuptools 2>/dev/null;
echo '===COMPOSE_GATEWAY_ENV===';
echo 'Tksc@197653' | sudo -S grep -B3 -A15 'pf-gateway-service:' /opt/cloudApi2_3.1/install/compose.yaml 2>/dev/null | grep -iE 'ISV|WEBHOOK|env_file|environment|image|container_name' ;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
