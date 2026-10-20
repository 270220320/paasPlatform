#!/usr/bin/env bash
# 导出 POI 环绕失败问题日志（UTC 2026-09-17 07:35-07:59 窗口，覆盖起飞→5次环绕失败→后续状态）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "bash -s" <<'REMOTE'
echo "Tksc@197653" | sudo -S -v 2>/dev/null
sudo bash <<'ROOT'
# 注意：不能用 set -e，grep 无匹配返回 1 会中断导出
EXP=/tmp/poi-exp
rm -rf $EXP /tmp/poi-exp.tar.gz
mkdir -p $EXP/logs
W='2026-09-17T07:3[5-9]|2026-09-17T07:4|2026-09-17T07:5'

echo ">>> extracting es-task (poi) ..."
L=$(docker inspect es-task --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$L" | grep -ai "poi" | grep -av "point" > $EXP/logs/01-es-task_poi_full.log

echo ">>> extracting tas-service window ..."
T=$(docker inspect tas-service --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$T" | grep -aE "$W" > $EXP/.tas_window.log
{
  grep -aiE "poi" $EXP/.tas_window.log | grep -av "point"
  echo "===== services_reply lines in window (device acks, if any) ====="
  grep -a "services_reply" $EXP/.tas_window.log | head -50
  echo "===== return_home_info lines in window ====="
  grep -a "return_home_info" $EXP/.tas_window.log | head -10
} > $EXP/logs/02-tas-service_poi_commands.log

echo ">>> extracting device OSD snapshots ..."
{
  echo "===== [1] drone OSD 07:43:02 (right before 1st poi-enter; mode_code/height/position) ====="
  grep -a '"2026-09-17T07:43:02' $EXP/.tas_window.log | grep -a "1581F6Q8X252U00G05BU/osd" | head -1
  echo
  echo "===== [2] drone OSD 07:49:30 (right before last poi-enter) ====="
  grep -a '"2026-09-17T07:49:30' $EXP/.tas_window.log | grep -a "1581F6Q8X252U00G05BU/osd" | head -1
  echo
  echo "===== [3] dock OSD snapshots containing drc_state (7CTDM6F00BQY6R) ====="
  grep -a "thing/product/7CTDM6F00BQY6R" $EXP/.tas_window.log | grep -a '"drc_state"' | tail -2
  echo
  echo "===== [4] drc_state value samples (all, chronological) ====="
  grep -ao 'drc_state[^,]*' $EXP/.tas_window.log | sort | uniq -c | sort -rn | head -10
} > $EXP/logs/05-device-osd-snapshots.log

echo ">>> extracting gateway / es-management poi lines ..."
G=$(docker inspect pf-gateway-service --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$G" | grep -aE "$W" | grep -ai "poi" > $EXP/logs/03a-pf-gateway-service_poi.log 2>/dev/null || true
M=$(docker inspect es-management --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$M" | grep -aE "$W" | grep -ai "poi" > $EXP/logs/03b-es-management_poi.log 2>/dev/null || true

echo ">>> extracting es-drc-service (DRC session background, noise filtered) ..."
D=$(docker inspect es-drc-service --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$D" | grep -aE "$W" | grep -avE "evalsha|redis_hook|auto_record|/drc/health|check_heartbeat|cron-" > $EXP/logs/04-es-drc-service_window.log || true

rm -f $EXP/.tas_window.log
echo "===== exported files ====="
ls -lh $EXP/logs/
tar czf /tmp/poi-exp.tar.gz -C $EXP logs
chmod 644 /tmp/poi-exp.tar.gz
echo "===== archive ====="
ls -lh /tmp/poi-exp.tar.gz
ROOT
REMOTE
echo "===== scp back to local ====="
setsid -w scp -o StrictHostKeyChecking=no test@192.168.76.141:/tmp/poi-exp.tar.gz /mnt/d/AiFlow/scripts/poi-exp-20260917.tar.gz 2>&1
ls -lh /mnt/d/AiFlow/scripts/poi-exp-20260917.tar.gz
