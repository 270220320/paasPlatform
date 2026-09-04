#!/usr/bin/env bash
# 云服务切换失败排查（十）：今日最新切换尝试的 services_reply result 码 + 下发参数
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TODAY_CLOUD_ENV_SWITCH_ALL===;
docker logs tas-service --since 6h 2>&1 | grep -a "cloud_environment" | tail -30 | cut -c1-1200;
echo ===RECENT_SERVICES_REPLY===;
docker logs tas-service --since 6h 2>&1 | grep -a "services_reply" | tail -10 | cut -c1-1200;
echo ===PF_DEVICE_SWITCH_LOGS===;
docker logs pf-device-service --since 6h 2>&1 | grep -aiE "cloud.environment|switch|210271|325001" | tail -20 | cut -c1-800;
echo ===DOCKER_PS_TAS_PFDEVICE===;
docker ps --filter name=tas-service --filter name=pf-device-service --format "{{.Names}} {{.Status}}";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
