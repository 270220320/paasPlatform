#!/usr/bin/env bash
# 2026-09-21 IP 切换检查2：完整残留文件清单、真实瓦片路径、司空DB容器定位
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "===== 1. ALL FILES containing 76.141 (install dir, excl logs) ====="
echo "Tksc@197653" | sudo -S grep -rl "76\.141" /opt/cloudApi2_3.1/install/ 2>/dev/null | grep -vE "\.log$|\.bak"
echo "===== 2. REAL TILE FILES (first 5) ====="
echo "Tksc@197653" | sudo -S find /var/lib/flighthub2/asset-map/png-tiles -type f 2>/dev/null | head -5
echo "===== 3. MYSQL/MARIA CONTAINERS ====="
echo "Tksc@197653" | sudo -S docker ps --format "{{.Names}}\t{{.Status}}" | grep -iE "mysql|maria"
echo "===== 4. compose config dir & env files ====="
ls -la /opt/cloudApi2_3.1/install/ | grep -E "\.env|yaml$" | head -10
echo "===== 5. residue also in /opt/cloudApi2_3 (3.0 old dir, info only) ====="
echo "Tksc@197653" | sudo -S grep -rl "76\.141" /opt/cloudApi2_3/install/ 2>/dev/null | grep -vE "\.log$|\.bak" | head -5
echo "===== CHECK2 DONE ====="
REMOTE
