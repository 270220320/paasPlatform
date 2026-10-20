#!/usr/bin/env bash
# 诊断 POI 环绕飞行失败：send enter poi mode command is failed
# 扫描 76.141 司空云端各容器日志中的 poi 相关记录
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
echo "===== CONTAINER LIST ====="
echo "Tksc@197653" | sudo -S docker ps --format "{{.Names}}\t{{.Status}}" 2>/dev/null
echo "===== POI LOG SCAN (all containers, last 48h) ====="
for c in $(echo "Tksc@197653" | sudo -S docker ps --format "{{.Names}}" 2>/dev/null); do
  hits=$(echo "Tksc@197653" | sudo -S docker logs --since 48h "$c" 2>&1 | grep -iE "poi|enter poi" | grep -viE "point|pointer" | tail -40)
  if [ -n "$hits" ]; then
    echo "### $c ###"
    echo "$hits"
    echo
  fi
done
echo "===== SCAN DONE ====="
' 2>&1
