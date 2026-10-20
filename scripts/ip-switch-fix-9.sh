#!/usr/bin/env bash
# 2026-09-21 IP 切换修复9：force-recreate simulator + es-privatization-system 日志排查
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
echo "===== 1. simulator minio env in compose ====="
sudo grep -A 40 "pf-device-simulator" /opt/cloudApi2_3.1/install/compose.yaml | grep -iE "minio|endpoint|30802" | head -6
echo "===== 2. force-recreate simulator ====="
cd /opt/cloudApi2_3.1/install && sudo docker compose --env-file /opt/cloudApi2_3.1/fh2-privatization-install-packages/env -f compose.yaml up -d --force-recreate pf-device-simulator-service 2>&1 | tail -4
sleep 10
sudo docker ps -a --format "{{.Names}}\t{{.Status}}" | grep simulator
echo "===== 3. es-privatization-system last logs ====="
sudo docker logs --tail 15 es-privatization-system 2>&1 | tail -15
echo "===== 4. FINAL: restarting/unhealthy ====="
sudo docker ps --format "{{.Names}}\t{{.Status}}" | grep -iE "restart|unhealthy" || echo "NONE - all stable"
echo "===== FIX9 DONE ====="
REMOTE
