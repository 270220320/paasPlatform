#!/usr/bin/env bash
# 219015 排查（七）：es-task 在 09:28:50 窗口的完整处理日志（SQL+判定）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===ES_TASK_WINDOW_092850===';
echo 'Tksc@197653' | sudo -S docker logs es-task --since 6h 2>&1 | grep -a "09:28:50" | grep -avE "relay_missions|dashboard" | head -25 | cut -c1-700;
echo '===TASK_TYPE_STRINGS===';
echo 'Tksc@197653' | sudo -S docker logs es-task --since 24h 2>&1 | grep -a "task type" | grep -av "unsupported" | head -5 | cut -c1-400;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
