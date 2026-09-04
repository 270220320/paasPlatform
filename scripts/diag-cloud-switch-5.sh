#!/usr/bin/env bash
# 云服务切换失败排查（五）：原生表单细节——tcp://与mqtt://各自上下文 + 地址校验正则
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TCP_CONTEXT===;
docker exec frontend sh -c "grep -rhoaE \".{100}tcp://host:1883.{100}\" /app/uranus-device-manager-app /app/project-app --include=*.js 2>/dev/null | head -4";
echo ===MQTT_CONTEXT===;
docker exec frontend sh -c "grep -rhoaE \".{100}mqtt://host:1883.{100}\" /app/uranus-device-manager-app /app/project-app --include=*.js 2>/dev/null | head -4";
echo ===ADDR_VALIDATION===;
docker exec frontend sh -c "grep -rhoaE \".{80}(pattern|validat|regex|placeholder)[^,]{0,120}(tcp|mqtt)://.{60}\" /app/uranus-device-manager-app --include=*.js 2>/dev/null | head -5";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
