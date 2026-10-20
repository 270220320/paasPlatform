#!/usr/bin/env bash
# 诊断 POI 环绕失败第6步：tas-service / tas-emqx-service 下发与设备 ack + 设备机型确认
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "bash -s" <<'REMOTE'
echo "Tksc@197653" | sudo -S -v 2>/dev/null
sudo bash <<'ROOT'
echo "===== TAS-SERVICE poi window ====="
TASLOG=$(docker inspect tas-service --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$TASLOG" | grep -a '2026-09-17T07:4[3-9]' | grep -aiE "poi" | head -30
echo
echo "===== TAS-EMQX-SERVICE poi window ====="
TELOG=$(docker inspect tas-emqx-service --format '{{.LogPath}}' 2>/dev/null)
tr -d '\000' < "$TELOG" | grep -a '2026-09-17T07:4[3-9]' | grep -aiE "poi|7CTDM6F00BQY6R" | grep -av "point" | head -40
echo
echo "===== EMQX clients (retry, table fmt) ====="
docker exec emqx emqx ctl clients list 2>&1 | head -5
echo "===== EMQX: search by clientid ====="
docker exec emqx emqx ctl clients list 2>/dev/null | grep -aiE "7CTDM6F|1581F6Q" | head
echo
echo "===== DEVICE MODEL LOOKUP (postgres) ====="
docker exec postgres_instance_1 psql -U postgres -Atc "SELECT datname FROM pg_database WHERE NOT dattemplate;" 2>/dev/null
echo "===== DONE ====="
ROOT
REMOTE
