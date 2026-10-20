#!/usr/bin/env bash
# 诊断 POI 环绕失败第2步：es-task / es-drc-service 在失败时间窗(UTC 07:42-07:51)的日志
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
S="echo Tksc@197653 | sudo -S"
echo "===== ES-TASK POI WINDOW (07:42-07:51 UTC) ====="
$S docker logs es-task --since "2026-09-17T07:42:00Z" --until "2026-09-17T07:51:00Z" 2>&1 | grep -iE "poi" | head -60
echo
echo "===== ES-TASK REQ TRACE (request_id of poi-enter 1507adb9) ====="
$S docker logs es-task --since "2026-09-17T07:42:00Z" --until "2026-09-17T07:51:00Z" 2>&1 | grep -E "1507adb9|5d932159|000ff86b|2d101ff2|d7800b7e" | head -40
echo
echo "===== ES-DRC-SERVICE WINDOW (07:40-07:51 UTC) ====="
$S docker logs es-drc-service --since "2026-09-17T07:40:00Z" --until "2026-09-17T07:51:00Z" 2>&1 | grep -viE "evalsha|redis_hook" | head -50
echo
echo "===== EMQX CLIENTS (drone/dock/drc) ====="
$S docker exec emqx emqx ctl clients list 2>/dev/null | grep -iE "7CTDM6F00BQY6R|1581F6Q8X252U00G05BU|drc" | head -20
echo "===== DONE ====="
' 2>&1
