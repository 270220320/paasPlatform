#!/usr/bin/env bash
# 219015 排查（五）：es-task 容器对该 PUT /status 的完整日志（校验细节）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===ES_TASK_PUT_STATUS_LOG===';
echo 'Tksc@197653' | sudo -S docker logs es-task --since 3h 2>&1 | grep -a "flight-tasks/.*/status" | head -6 | cut -c1-1200;
echo '===ES_TASK_STATUS_ERR===';
echo 'Tksc@197653' | sudo -S docker logs es-task --since 3h 2>&1 | grep -aiE "219015|can not update|status.*invalid|update.*status" | grep -av "flight-tasks/.*/status\"" | head -8 | cut -c1-800;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
