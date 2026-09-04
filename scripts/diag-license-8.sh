#!/usr/bin/env bash
# 诊断（八）：容器日志中搜 uuid a12f2ee2 + devops 服务 license 相关接口日志
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
SN=a12f2ee2;
echo ===DEVOPS_BE_LOGS===;
docker logs es310-privatization-devops-be --since 720h 2>&1 | grep -aiE "licen|$SN" | tail -20;
echo ===GATEWAY_LOGS===;
docker logs pf-gateway-service --since 720h 2>&1 | grep -aiE "$SN" | tail -10;
echo ===TRAEFIK_LOGS===;
docker logs traefik --since 720h 2>&1 | grep -ai "$SN" | tail -10;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
