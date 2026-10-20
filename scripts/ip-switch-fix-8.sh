#!/usr/bin/env bash
# 2026-09-21 IP 切换修复8：force-recreate forbid-service + 排查 es-privatization-system/simulator
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
echo "===== 1. force-recreate forbid-service ====="
cd /opt/cloudApi2_3.1/install && sudo docker compose --env-file /opt/cloudApi2_3.1/fh2-privatization-install-packages/env -f compose.yaml up -d --force-recreate forbid-service 2>&1 | tail -5
sleep 10
sudo docker ps -a --format "{{.Names}}\t{{.Status}}" | grep forbid
echo "===== 2. es-privatization-system: health & config residue ====="
sudo docker inspect es-privatization-system --format '{{json .State.Health.Log}}' 2>/dev/null | head -c 800; echo
sudo grep -rn "76\.141" /opt/cloudApi2_3.1/es-privatization-system/ 2>/dev/null | grep -vE "\.log$" | head -8 || echo "es-dir clean"
echo "===== 3. pf-device-simulator-service last logs ====="
sudo docker logs --tail 6 pf-device-simulator-service 2>&1 | tail -6
echo "===== 4. FINAL: restarting/unhealthy list ====="
sudo docker ps --format "{{.Names}}\t{{.Status}}" | grep -iE "restart|unhealthy" || echo "NONE - all stable"
echo "===== 5. FINAL: gateway + tile ====="
curl -sS -o /dev/null -w "30812 HTTP %{http_code}\n" --connect-timeout 5 http://192.168.76.129:30812/
curl -sS -o /dev/null -w "30813 tile HTTP %{http_code}\n" --connect-timeout 5 http://192.168.76.129:30813/png-tiles_1/11/1690/779.jpg
echo "===== FIX8 DONE ====="
REMOTE
