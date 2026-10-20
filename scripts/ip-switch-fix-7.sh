#!/usr/bin/env bash
# 2026-09-21 IP 切换修复7：清理误建证书目录，复制真实证书文件，重启消费者
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
echo "===== 1. remove bogus dirs, copy real cert files ====="
sudo rm -rf /opt/cloudApi2_3.1/data/cert/192.168.76.129/ssl.crt /opt/cloudApi2_3.1/data/cert/192.168.76.129/ssl.key
sudo cp -a /opt/cloudApi2_3.1/data/cert/192.168.76.141/ssl.crt /opt/cloudApi2_3.1/data/cert/192.168.76.141/ssl.key /opt/cloudApi2_3.1/data/cert/192.168.76.129/
sudo ls -la /opt/cloudApi2_3.1/data/cert/192.168.76.129/
echo "===== 2. cert check ====="
sudo openssl x509 -in /opt/cloudApi2_3.1/data/cert/192.168.76.129/ssl.crt -noout -subject -enddate 2>&1 | head -3
echo "===== 3. which service mounts /resource/certs/https.crt ====="
sudo awk 'NR>=800 && NR<=850 && /container_name:/' /opt/cloudApi2_3.1/install/compose.yaml
echo "===== 4. restart forbid-service ====="
sudo docker restart forbid-service 2>&1
sleep 15
sudo docker ps -a --format "{{.Names}}\t{{.Status}}" | grep -E "forbid"
echo "===== 5. final check: restarting/unhealthy ====="
sudo docker ps --format "{{.Names}}\t{{.Status}}" | grep -iE "restart|unhealthy" || echo "NONE - all stable"
echo "===== FIX7 DONE ====="
REMOTE
