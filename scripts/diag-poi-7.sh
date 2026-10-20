#!/usr/bin/env bash
# 诊断 POI 环绕失败第7步：poi_mode_enter 发布记录 + 7CTDM6F00BQY6R 的 drc_state + DRC 会话生命周期
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "bash -s" <<'REMOTE'
echo "Tksc@197653" | sudo -S -v 2>/dev/null
sudo bash <<'ROOT'
TASLOG=$(docker inspect tas-service --format '{{.LogPath}}' 2>/dev/null)
echo "===== 1. TAS poi_mode_enter publish/timeout (07:49:30-07:49:42) ====="
tr -d '\000' < "$TASLOG" | grep -a '2026-09-17T07:49:3\|2026-09-17T07:49:4' | grep -aiE "poi_mode_enter|services|publish.*poi|timeout" | head -20
echo
echo "===== 2. TAS services topic to 7CTDM6F00BQY6R around poi (07:43:30-07:44:40) ====="
tr -d '\000' < "$TASLOG" | grep -a '2026-09-17T07:4[34]' | grep -a "thing/product/7CTDM6F00BQY6R/services" | head -20
echo
echo "===== 3. DRC state of dock 7CTDM6F00BQY6R (osd snapshots, sample) ====="
tr -d '\000' < "$TASLOG" | grep -a 'thing/product/7CTDM6F00BQY6R/osd' | grep -ao '"drc_state":{"timestamp":[0-9]*,"value":[0-9]*}' | tail -5
tr -d '\000' < "$TASLOG" | grep -a '2026-09-17T07:4' | grep -a 'thing/product/7CTDM6F00BQY6R/osd' | grep -ao 'drc_state[^,]*' | tail -5
echo
echo "===== 4. es-drc-service DRC session lifecycle (07:30-07:55) ====="
DRCLOG=$(docker inspect es-drc-service --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$DRCLOG" | grep -a '2026-09-17T07:3\|2026-09-17T07:4\|2026-09-17T07:5' | grep -aiE "drc.*(enter|exit|connect|disconnect|close|heartbeat)|osc" | grep -avE "evalsha|redis_hook" | head -30
echo
echo "===== 5. drone mode_code samples (07:43-07:50) ====="
tr -d '\000' < "$TASLOG" | grep -a '2026-09-17T07:4' | grep -a 'thing/product/1581F6Q8X252U00G05BU/osd' | grep -ao 'mode_code[^,]*' | tail -8
echo "===== DONE ====="
ROOT
REMOTE
