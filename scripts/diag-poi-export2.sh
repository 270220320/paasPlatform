#!/usr/bin/env bash
# 恢复 tas-service 日志（检查轮转文件）并重新生成 02/05 后重打包
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "bash -s" <<'REMOTE'
echo "Tksc@197653" | sudo -S -v 2>/dev/null
sudo bash <<'ROOT'
CID=$(docker inspect tas-service --format '{{.Id}}' 2>/dev/null)
echo "===== tas-service container log dir ====="
ls -lh /var/lib/docker/containers/$CID/ | head -10
echo "===== docker log config ====="
docker inspect tas-service --format '{{.HostConfig.LogConfig}}' 2>/dev/null
echo
W='2026-09-17T07:3[5-9]|2026-09-17T07:4|2026-09-17T07:5'
EXP=/tmp/poi-exp
mkdir -p $EXP/logs
# 把主日志 + 所有轮转归档合并扫描
cat /var/lib/docker/containers/$CID/*-json.log* 2>/dev/null | tr -d '\000' | grep -aE "$W" > $EXP/.tas_window.log
echo "===== window line count: $(wc -l < $EXP/.tas_window.log) ====="
grep -c "poi_mode_enter" $EXP/.tas_window.log
{
  grep -aiE "poi_mode_enter|poi_task|poi-enter" $EXP/.tas_window.log | grep -av "point"
  echo "===== services_reply lines in window (device acks, if any) ====="
  grep -a "services_reply" $EXP/.tas_window.log | head -50
  echo "===== return_home_info lines in window ====="
  grep -a "return_home_info" $EXP/.tas_window.log | head -10
} > $EXP/logs/02-tas-service_poi_commands.log
{
  echo "===== [1] drone OSD 07:43:02 (right before 1st poi-enter) ====="
  grep -a '2026-09-17T07:43:02' $EXP/.tas_window.log | grep -a "1581F6Q8X252U00G05BU/osd" | head -1
  echo
  echo "===== [2] drone OSD 07:49:30 (right before last poi-enter) ====="
  grep -a '2026-09-17T07:49:30' $EXP/.tas_window.log | grep -a "1581F6Q8X252U00G05BU/osd" | head -1
  echo
  echo "===== [3] dock OSD snapshots containing drc_state (7CTDM6F00BQY6R) ====="
  grep -a "thing/product/7CTDM6F00BQY6R" $EXP/.tas_window.log | grep -a '"drc_state"' | tail -2
  echo
  echo "===== [4] drc_state value distribution in window ====="
  grep -ao '"drc_state":{"timestamp":[0-9]*,"value":[0-9]*}' $EXP/.tas_window.log | grep -o 'value":[0-9]*' | sort | uniq -c
  echo "===== [5] drone mode_code distribution in window ====="
  grep -a "1581F6Q8X252U00G05BU/osd" $EXP/.tas_window.log | grep -ao 'mode_code[^,]*' | sort | uniq -c | head -6
} > $EXP/logs/05-device-osd-snapshots.log
rm -f $EXP/.tas_window.log
echo "===== regenerated ====="
ls -lh $EXP/logs/
tar czf /tmp/poi-exp.tar.gz -C $EXP logs
chmod 644 /tmp/poi-exp.tar.gz
ls -lh /tmp/poi-exp.tar.gz
ROOT
REMOTE
echo "===== scp back ====="
setsid -w scp -o StrictHostKeyChecking=no test@192.168.76.141:/tmp/poi-exp.tar.gz /mnt/d/AiFlow/scripts/poi-exp-20260917.tar.gz 2>&1
ls -lh /mnt/d/AiFlow/scripts/poi-exp-20260917.tar.gz
