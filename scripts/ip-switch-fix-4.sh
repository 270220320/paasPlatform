#!/usr/bin/env bash
# 2026-09-21 IP 切换修复4：重建后全面验证
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
echo "===== 1. container stats ====="
echo "total: $(sudo docker ps -a --format '{{.Names}}' | wc -l), running: $(sudo docker ps --format '{{.Names}}' | wc -l)"
echo "-- not-up containers:"
sudo docker ps -a --format "{{.Names}}\t{{.Status}}" | grep -viE "\tup( |,)" | head -15 || echo "(all up)"
echo "-- restarting containers:"
sudo docker ps --format "{{.Names}}\t{{.Status}}" | grep -i restart || echo "none"
echo "===== 2. gateway 30812 ====="
curl -sS -o /dev/null -w "HTTP %{http_code}\n" --connect-timeout 5 http://192.168.76.129:30812/
echo "===== 3. real tile 30813 ====="
curl -sS -o /dev/null -w "HTTP %{http_code} size=%{size_download}\n" --connect-timeout 5 http://192.168.76.129:30813/png-tiles_1/11/1690/779.jpg
echo "===== 4. forbid-service last logs ====="
sudo docker logs --tail 8 forbid-service 2>&1 | tail -8
echo "===== 5. fh2-system project dir residue ====="
sudo grep -rl "76\.141" /opt/cloudApi2_3.1/es-privatization-system/ 2>/dev/null | grep -vE "\.log$" | head -5 || echo "clean"
echo "===== 6. docker compose ps summary ====="
cd /opt/cloudApi2_3.1/install && sudo docker compose --env-file /opt/cloudApi2_3.1/fh2-privatization-install-packages/env -f compose.yaml ps --format "{{.State}}" 2>/dev/null | sort | uniq -c
echo "===== FIX4 DONE ====="
REMOTE
