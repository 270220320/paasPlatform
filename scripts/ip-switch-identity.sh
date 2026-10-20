#!/usr/bin/env bash
# 验证 76.141 上是否还是司空服务器，及其当前实际 IP 配置
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 'bash -s' <<'REMOTE' 2>&1
echo "===== hostname ====="
hostname
echo "===== current IPv4 ====="
ip -4 addr show | grep -E "inet " | grep -v "127.0.0.1"
echo "===== default route ====="
ip route show default
echo "===== docker containers (top5) ====="
echo "Tksc@197653" | sudo -S docker ps --format "{{.Names}}\t{{.Status}}" 2>/dev/null | head -5
echo "===== IDENTITY DONE ====="
REMOTE
