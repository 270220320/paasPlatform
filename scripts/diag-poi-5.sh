#!/usr/bin/env bash
# 诊断 POI 环绕失败第5步：DRC 链路状态 + EMQX 订阅 + es-drc-service 会话
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "bash -s" <<'REMOTE'
echo "Tksc@197653" | sudo -S -v 2>/dev/null
sudo bash <<'ROOT'
echo "===== ES-TASK DRC/SEND LINES (07:43-07:50) ====="
LOGPATH=$(docker inspect es-task --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$LOGPATH" | grep -a '2026-09-17T07:4[3-9]' | grep -aiE "drc" | grep -avE "poi_tasks|topic_atc" | head -30
echo
echo "===== ES-DRC-SERVICE (07:35-07:51, no redis noise) ====="
DRCLOG=$(docker inspect es-drc-service --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$DRCLOG" | grep -a '2026-09-17T07:3[5-9]\|2026-09-17T07:4\|2026-09-17T07:50' | grep -avE "evalsha|redis_hook" | head -40
echo
echo "===== EMQX CLIENTS (dock/drone SN) ====="
docker exec emqx emqx ctl clients list 2>/dev/null | grep -aE "7CTDM6F00BQY6R|1581F6Q8X252U00G05BU" | head -10
echo
echo "===== EMQX SUBSCRIPTIONS drc topic ====="
docker exec emqx emqx ctl subscriptions list 2>/dev/null | grep -aiE "drc" | head -20
echo "===== DONE ====="
ROOT
REMOTE
