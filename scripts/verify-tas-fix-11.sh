#!/usr/bin/env bash
# 修复验证（十七）：无人机当前在线状态快查（用户即将重新创建任务）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
curl -s -m 10 -X POST 'http://127.0.0.1:38081/thing-access-service/inner/tas/topos/list' -H 'Content-Type: application/json' -d '{"sns":["8UUXN2K00A0144"]}' | grep -aoE '"sn":"[^"]*","device_model":"[a-z]*"[^}]*online_status":(true|false)' | head -4;
echo '---drone_osd_recent---';
echo 'Tksc@197653' | sudo -S docker logs tas-service --since 3m 2>&1 | grep -ac '"topic":"thing/product/1581F8HGX252500A009X/osd"'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
