#!/usr/bin/env bash
# 2026-09-21 IP 切换修复6：证书目录按 IP 命名，复制 141 证书到 129 目录并重启依赖容器
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
echo "===== 1. cert dirs ====="
sudo ls -la /opt/cloudApi2_3.1/data/cert/
echo "===== 2. copy 141 certs -> 129 dir ====="
sudo mkdir -p /opt/cloudApi2_3.1/data/cert/192.168.76.129
sudo cp -a /opt/cloudApi2_3.1/data/cert/192.168.76.141/ssl.crt /opt/cloudApi2_3.1/data/cert/192.168.76.141/ssl.key /opt/cloudApi2_3.1/data/cert/192.168.76.129/ 2>&1
sudo ls -la /opt/cloudApi2_3.1/data/cert/192.168.76.129/
echo "===== 3. cert validity & SAN ====="
sudo openssl x509 -in /opt/cloudApi2_3.1/data/cert/192.168.76.129/ssl.crt -noout -subject -enddate -ext subjectAltName 2>&1 | head -6
echo "===== 4. restart consumers (forbid-service + traefik) ====="
sudo docker restart forbid-service traefik 2>&1
sleep 12
echo "===== 5. status of cert consumers ====="
sudo docker ps -a --format "{{.Names}}\t{{.Status}}" | grep -E "forbid|^traefik"
echo "===== 6. gateway still alive ====="
curl -sS -o /dev/null -w "30812 HTTP %{http_code}\n" --connect-timeout 5 http://192.168.76.129:30812/
echo "===== 7. final not-up list ====="
sudo docker ps -a --format "{{.Names}}\t{{.Status}}" | grep -viE "\tup( |,)" | head -10 || echo "(all up)"
echo "===== FIX6 DONE ====="
REMOTE
