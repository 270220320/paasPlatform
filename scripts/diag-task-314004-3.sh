#!/usr/bin/env bash
# 诊断（三）：近 3 天 es-task 所有设备回复失败码 + 司空侧任务历史（找 GNSS 失败案例）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===CMD_REPLY_NONZERO_3DAYS===;
docker logs --since 72h es-task 2>&1 | grep -a "handle cmd reply" | grep -avE "result: 0[,} ]|result.:0[,} ]" | tail -30;
echo ===TASK_END_REASON_3DAYS===;
docker logs --since 72h es-task 2>&1 | grep -a "task end log" | tail -20;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
