#!/usr/bin/env bash
# 2026-09-21 IP 切换修复10：simulator 地址来源排查 + es-privatization-system 重启自愈
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
echo "===== 1. simulator fresh logs (which address now) ====="
sudo docker logs --since 2m pf-device-simulator-service 2>&1 | grep -oE "http://[0-9.]+:30802" | sort | uniq -c | head -3
sudo docker logs --tail 4 pf-device-simulator-service 2>&1 | tail -4
echo "===== 2. simulator env (minio related) ====="
sudo docker inspect pf-device-simulator-service --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -iE "minio|endpoint|30802|76\." | head -8
echo "===== 3. simulator mounts ====="
sudo docker inspect pf-device-simulator-service --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}' | head -8
echo "===== 4. restart es-privatization-system ====="
sudo docker restart es-privatization-system 2>&1
sleep 25
sudo docker ps -a --format "{{.Names}}\t{{.Status}}" | grep es-privatization
echo "===== 5. es logs tail ====="
sudo docker logs --tail 5 es-privatization-system 2>&1 | tail -5
echo "===== FIX10 DONE ====="
REMOTE
