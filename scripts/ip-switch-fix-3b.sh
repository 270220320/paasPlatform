#!/usr/bin/env bash
# 2026-09-21 IP 切换修复3b：docker compose up -d 重建容器栈使新 IP env 生效
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
echo "===== 0. restarting containers BEFORE ====="
sudo docker ps --format "{{.Names}}\t{{.Status}}" | grep -i restart || echo "none restarting"
echo "===== 1. COMPOSE UP -D (may take minutes) ====="
cd /opt/cloudApi2_3.1/install && sudo docker compose --env-file /opt/cloudApi2_3.1/fh2-privatization-install-packages/env -f compose.yaml up -d --remove-orphans 2>&1 | tail -50
echo "===== 2. STATUS AFTER ====="
sudo docker ps -a --format "{{.Names}}\t{{.Status}}" | grep -viE "\tup( |,)" | head -15 || true
echo "-- total: $(sudo docker ps -a --format '{{.Names}}' | wc -l), running: $(sudo docker ps --format '{{.Names}}' | wc -l)"
echo "===== 3. fh2-system project dir residue check ====="
sudo grep -rl "76\.141" /opt/cloudApi2_3.1/es-privatization-system/ 2>/dev/null | grep -vE "\.log$" | head -5 || echo "clean"
echo "===== FIX3b DONE ====="
REMOTE
