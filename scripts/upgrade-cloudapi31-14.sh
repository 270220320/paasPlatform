#!/usr/bin/env bash
# 司空3.1升级（十四）：实测设备接口验证 webhook 鉴权修复
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===GATEWAY_CONTAINER_STATUS===';
echo 'Tksc@197653' | sudo -S docker inspect pf-gateway-service --format 'StartedAt={{.State.StartedAt}} Created={{.Created}}' 2>/dev/null;
echo '===GATEWAY_ENV_FULL===';
echo 'Tksc@197653' | sudo -S docker exec pf-gateway-service env 2>/dev/null | sort | head -40;
echo '===TEST_DEVICE_API===';
curl -s -m 10 -H 'auth_keys: user_id' -H 'user_id: paas_test' http://192.168.76.141:30812/gateway/device/api/v1/manager/devices | head -c 600;
echo;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
