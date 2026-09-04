#!/usr/bin/env bash
# 云服务切换失败排查（一）：traefik 请求/响应 + 定位处理服务 + tas 下发指令
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TRAEFIK_SWITCH_RESP===;
docker logs traefik --since 30m 2>&1 | grep -a cloud-environment-switch | tail -3 | cut -c1-1200;
echo ===DEVICE_SERVICE_CANDIDATES===;
docker ps --format "{{.Names}}" | grep -aiE "device|manager" | head -8;
echo ===SWITCH_IN_ALL_LOGS===;
for c in $(docker ps --format "{{.Names}}" | grep -aiE "pf-|device|manager"); do
  N=$(docker logs $c --since 30m 2>&1 | grep -ac "cloud-environment-switch\|cloud_environment\|CloudEnvironment");
  if [ "$N" != "0" ]; then echo "HIT $c: $N lines"; fi
done;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
