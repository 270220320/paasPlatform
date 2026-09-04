#!/usr/bin/env bash
# 云服务切换失败排查（十一）：HTTP 层痕迹（traefik access log + pf-gateway）+ EMQX 状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TRAEFIK_ACCESSLOG_SWITCH===;
docker exec traefik sh -c "grep -a cloud-environment /traefik/log/access.log 2>/dev/null | tail -6" | cut -c1-600;
echo ===PF_GATEWAY_SWITCH===;
docker logs pf-gateway-service --since 12h 2>&1 | grep -aiE "cloud.environment|switch" | tail -15 | cut -c1-600;
echo ===PF_DEVICE_ERROR_RECENT===;
docker logs pf-device-service --since 12h 2>&1 | grep -aiE "error|fail|denied|401|403" | tail -15 | cut -c1-500;
echo ===EMQX_STATUS===;
docker ps --filter name=emqx --format "{{.Names}} {{.Status}}";
echo ===TRAEFIK_UPTIME_NOTE===;
docker ps --filter name=traefik --format "{{.Names}} {{.Status}}";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
