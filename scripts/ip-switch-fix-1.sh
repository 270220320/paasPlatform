#!/usr/bin/env bash
# 2026-09-21 IP 切换修复1：备份 + sed 替换服务器侧配置文件 76.141 -> 76.129
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
S='echo Tksc@197653 | sudo -S'
echo "===== 0. .envpath content ====="
cat /opt/cloudApi2_3.1/install/.envpath 2>/dev/null || $S cat /opt/cloudApi2_3.1/install/.envpath 2>/dev/null
echo "===== 0b. mysql credentials in compose ====="
$S grep -nE "MYSQL_ROOT_PASSWORD|MYSQL_PASSWORD" /opt/cloudApi2_3.1/install/compose.yaml 2>/dev/null | sed 's/PASSWORD=.*/PASSWORD=<hidden>/' | head -5
echo "===== 1. BUILD FILE LIST ====="
FILES=$($S grep -rl "76\.141" /opt/cloudApi2_3.1/install/ /var/lib/flighthub2/asset-map/ 2>/dev/null | grep -vE "\.log$|backup-ip141" | awk '{print $NF}' | sort -u)
echo "$FILES"
echo "===== 2. BACKUP to /opt/backup-ip141-20260921 ====="
BK=/opt/backup-ip141-20260921
$S mkdir -p $BK
for f in $FILES; do
  $S cp -a --parents "$f" $BK/ 2>/dev/null && echo "backed: $f" || echo "BACKUP FAIL: $f"
done
echo "===== 3. SED REPLACE ====="
for f in $FILES; do
  $S sed -i 's/192\.168\.76\.141/192.168.76.129/g' "$f" 2>/dev/null && echo "replaced: $f" || echo "SED FAIL: $f"
done
echo "===== 4. VERIFY residue ====="
RES=$($S grep -rl "76\.141" /opt/cloudApi2_3.1/install/ /var/lib/flighthub2/asset-map/ 2>/dev/null | grep -vE "\.log$|backup-ip141" | awk '{print $NF}')
if [ -z "$RES" ]; then echo "CLEAN: no residue left"; else echo "RESIDUE LEFT:"; echo "$RES"; fi
echo "===== 5. map_config.yaml after ====="
$S cat /var/lib/flighthub2/asset-map/map_config.yaml 2>/dev/null
echo "===== FIX1 DONE ====="
REMOTE
