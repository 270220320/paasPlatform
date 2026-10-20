#!/usr/bin/env bash
# 2026-09-21 IP 切换修复3a：干跑确认 compose 调用方式
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
echo "===== 1. compose projects running ====="
sudo docker compose ls 2>/dev/null
echo "===== 2. install dir scripts ====="
ls /opt/cloudApi2_3.1/install/ | grep -vE "^(conf|data|deployment)$" | head -15
echo "===== 3. env file key vars ====="
sudo grep -vE "^#|^$" /opt/cloudApi2_3.1/fh2-privatization-install-packages/env | sed 's/=.*PASSWORD.*/=<hidden>/;s/PASSWORD=.*/PASSWORD=<hidden>/' | head -20
echo "===== 4. config --services dry parse (count) ====="
cd /opt/cloudApi2_3.1/install && sudo docker compose --env-file /opt/cloudApi2_3.1/fh2-privatization-install-packages/env -f compose.yaml config --services 2>/dev/null | wc -l
echo "===== 5. current running containers (count) ====="
sudo docker ps --format "{{.Names}}" | wc -l
echo "===== FIX3a DONE ====="
REMOTE
