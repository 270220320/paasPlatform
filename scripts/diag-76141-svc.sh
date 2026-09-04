#!/usr/bin/env bash
# 诊断 76.141：完整容器列表 + docker stats 瞬时值 + 关键端口探测
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
echo "===== DOCKER PS REST ====="; echo "Tksc@197653" | sudo -S docker ps -a --format "table {{.Names}}\t{{.Status}}" 2>/dev/null | tail -n +41
echo "===== DOCKER STATS (1-shot) ====="; echo "Tksc@197653" | sudo -S docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" 2>/dev/null | sort -t, -k2 -rn | head -15
echo "===== PORT 30812 (API gateway) ====="; curl -s -o /dev/null -w "HTTP %{http_code} in %{time_total}s\n" -m 8 http://127.0.0.1:30812/ || echo "CURL FAILED"
echo "===== PORT 56789 (route admin) ====="; curl -s -o /dev/null -w "HTTP %{http_code} in %{time_total}s\n" -m 8 http://127.0.0.1:56789/ || echo "CURL FAILED"
echo "===== LISTEN PORTS ====="; ss -tlnp 2>/dev/null | grep -E ":(30812|56789|80|443|1883|8883|9092)\b" | head -10
' 2>&1
