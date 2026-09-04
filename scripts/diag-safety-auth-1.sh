#!/usr/bin/env bash
# 诊断限飞区同步鉴权：查司空服务器容器与 dji2 后台实际端口
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
  echo "=== docker containers ==="
  echo "Tksc@197653" | sudo -S docker ps --format "{{.Names}}\t{{.Ports}}" 2>/dev/null
  echo "=== all listen ports ==="
  ss -tln | awk "{print \$4}" | sort -u
' 2>&1
