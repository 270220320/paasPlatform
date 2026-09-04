#!/usr/bin/env bash
# 云服务切换失败排查（二）：pf-gateway-service / pf-device-service 处理日志全文
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===PF_GATEWAY_LINES===;
docker logs pf-gateway-service --since 40m 2>&1 | grep -a "cloud-environment-switch\|cloud_environment\|CloudEnvironment" | cut -c1-1500;
echo ===PF_DEVICE_LINES===;
docker logs pf-device-service --since 40m 2>&1 | grep -a "cloud-environment-switch\|cloud_environment\|CloudEnvironment" | cut -c1-1500;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
