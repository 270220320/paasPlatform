#!/usr/bin/env bash
# 修复验证（三）：02:43-02:45 绑定操作完整链路 + 今天全天绑定 API 与 MQTT 指令
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===ESMGMT_WINDOW_0243_0245===';
SE docker logs es-management --since 24h 2>&1 | grep -aE '"time":"2026-09-11T02:4[3-5]' | grep -avE '"level":"info","msg":"\[0\.|device update redis|orgDevice:' | head -40 | cut -c1-450;
echo '===ESMGMT_BIND_API_TODAY===';
SE docker logs es-management --since 24h 2>&1 | grep -a '"path"' | grep -av 'inner/' | tail -20 | grep -aoE '"path":"[^"]*","referer[^}]*' | head -20;
echo '===TAS_BINDCFG_TODAY===';
SE docker logs tas-service --since 24h 2>&1 | grep -aiE 'airport_bind|bind_config|bind_status|organization' | tail -12 | cut -c1-650;
echo '===TAS_CONFIG_METHOD===';
SE docker logs tas-service --since 24h 2>&1 | grep -a '"method":"config"' | tail -4 | cut -c1-500;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
