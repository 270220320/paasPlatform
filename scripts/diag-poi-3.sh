#!/usr/bin/env bash
# 诊断 POI 环绕失败第3步：es-task 全量日志窗口（不 grep，直接看处理过程）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "bash -s" <<'REMOTE'
echo "Tksc@197653" | sudo -S docker logs es-task --since "2026-09-17T07:43:25Z" --until "2026-09-17T07:44:00Z" 2>&1 | head -100
echo "===== SECOND WINDOW 07:49:25-07:49:45 ====="
echo "Tksc@197653" | sudo -S docker logs es-task --since "2026-09-17T07:49:25Z" --until "2026-09-17T07:49:45Z" 2>&1 | head -60
echo "===== DONE ====="
REMOTE
