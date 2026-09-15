#!/usr/bin/env bash
# 司空3.1升级（十三）：配置 ISV_WEBHOOK_URL + setuptools update 生效
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
ENVF=/opt/cloudApi2_3.1/fh2-privatization-install-packages/env
echo '===BACKUP_ENV===';
echo 'Tksc@197653' | sudo -S cp "$ENVF" "${ENVF}.bak-webhook-fix" && echo backed_up;
echo '===SET_WEBHOOK_URL===';
echo 'Tksc@197653' | sudo -S sed -i 's|^ISV_WEBHOOK_URL=$|ISV_WEBHOOK_URL=http://pf-gateway-service:8080/gateway/test/webhook-auth|' "$ENVF";
grep -n 'ISV_WEBHOOK_URL' "$ENVF";
echo '===SETUPTOOLS_UPDATE===';
cd /opt/cloudApi2_3.1/fh2-privatization-install-packages && echo 'Tksc@197653' | sudo -S ./setuptools update 2>&1 | tail -25;
echo '===VERIFY_GATEWAY===';
echo 'Tksc@197653' | sudo -S docker exec pf-gateway-service sh -c 'env | grep -i webhook; grep -r "webhook_url" /app/config/ 2>/dev/null' 2>&1;
echo '===TEST_WEBHOOK_ENDPOINT===';
echo 'Tksc@197653' | sudo -S docker exec pf-gateway-service sh -c 'wget -q -O- --timeout=5 --post-data="{}" --header="Content-Type: application/json" http://127.0.0.1:8080/gateway/test/webhook-auth 2>&1 || curl -s -m 5 -X POST -H "Content-Type: application/json" -d "{}" http://127.0.0.1:8080/gateway/test/webhook-auth 2>&1' 2>&1;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
