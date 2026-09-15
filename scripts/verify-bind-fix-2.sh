#!/usr/bin/env bash
# 修复验证（二）：查用户刚才绑定操作的服务端记录（es-management API 调用与报错）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===ESMGMT_API_RECENT===';
SE docker logs es-management --since 4h 2>&1 | grep -a '"path"' | grep -avE 'GET|health|ping' | tail -30 | grep -aoE '"(path|method|code|end)":"?[^",}]*"?' | paste - - - - 2>/dev/null | tail -25;
echo '===ESMGMT_BIND_API_ALL===';
SE docker logs es-management --since 4h 2>&1 | grep -a '"path"' | grep -aiE 'bind|airport|device' | tail -15 | cut -c1-600;
echo '===ESMGMT_ERROR_RECENT===';
SE docker logs es-management --since 4h 2>&1 | grep -a '"level":"error"\|"level":"warn"' | tail -12 | cut -c1-600;
echo '===PF_DEVICE_BIND_API===';
SE docker logs pf-device-service --since 4h 2>&1 | grep -aiE 'bind' | grep -av 'gin/binding' | tail -10 | cut -c1-600;
echo '===TAS_BIND_MSG===';
SE docker logs tas-service --since 4h 2>&1 | grep -aiE 'airport_bind|bind_organization|device_bind' | tail -8 | cut -c1-700;
echo '===GATEWAY_443_API===';
SE docker logs traefik --since 4h 2>&1 | grep -aiE 'bind' | tail -10 | cut -c1-500;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
