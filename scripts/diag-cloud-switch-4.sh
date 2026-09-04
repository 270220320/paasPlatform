#!/usr/bin/env bash
# 云服务切换失败排查（四）：原生司空 UI JS 里 third cloud 表单的 mqtt 地址格式约束
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===NATIVE_UI_TCP_MQTT_DEFAULTS===;
docker exec frontend sh -c "grep -rhoaE \"(tcp|mqtt|ssl)://[a-zA-Z0-9_.:{},\\x27\\\"-]{0,30}\" /app --include=*.js 2>/dev/null | sort | uniq -c | sort -rn | head -15";
echo ===NATIVE_UI_SWITCH_FORM_HINT===;
docker exec frontend sh -c "grep -rhoaE \".{60}(mqtt_addr|mqttAddr).{100}\" /app --include=*.js 2>/dev/null | head -6";
echo ===NATIVE_UI_CLOUD_SWITCH_WORDS===;
docker exec frontend sh -c "grep -rla cloud_environment_switch /app --include=*.js 2>/dev/null | head -3";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
