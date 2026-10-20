#!/usr/bin/env bash
# 诊断 POI 环绕失败第8步：es-drc-service 失败瞬间全量日志 + tas-service poi_mode_enter 完整链
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "bash -s" <<'REMOTE'
echo "Tksc@197653" | sudo -S -v 2>/dev/null
sudo bash <<'ROOT'
echo "===== 1. ES-DRC-SERVICE full lines 07:49:31-07:49:42 ====="
DRCLOG=$(docker inspect es-drc-service --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$DRCLOG" | grep -a '2026-09-17T07:49:3\|2026-09-17T07:49:4' | grep -av '"level":"info","@timestamp":"2026-09-17T07:49:3[0-9]Z","caller":"repo/live.go"' | head -40
echo
echo "===== 2. TAS-SERVICE poi_mode_enter all mentions ====="
TASLOG=$(docker inspect tas-service --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$TASLOG" | grep -a 'poi_mode_enter' | head -10
echo
echo "===== 3. TAS-SERVICE drc topic publish 07:49:31-07:49:42 ====="
tr -d '\000' < "$TASLOG" | grep -a '2026-09-17T07:49:3\|2026-09-17T07:49:4' | grep -aE '/drc|drc/' | head -15
echo
echo "===== 4. poi_tasks table (postgres/timescaledb) ====="
for db in es310 tas es_task; do
  echo "-- try db: $db"
  docker exec postgres_instance_1 psql -U postgres -d "$db" -Atc "SELECT id,sn,drone_sn,status,created_at,updated_at FROM poi_tasks ORDER BY created_at DESC LIMIT 8;" 2>/dev/null && break
done
echo "===== DONE ====="
ROOT
REMOTE
