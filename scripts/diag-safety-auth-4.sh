#!/usr/bin/env bash
# 诊断限飞区同步鉴权 4：输出 traefik dynamic_conf.yaml 全文与文件列表
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
  echo "=== ls conf dir ==="
  echo "Tksc@197653" | sudo -S ls -la /opt/cloudApi2_3/install/conf/middleware-service/traefik/ 2>&1 | head -10
  echo "=== dynamic_conf.yaml content ==="
  echo "Tksc@197653" | sudo -S cat /opt/cloudApi2_3/install/conf/middleware-service/traefik/dynamic_conf.yaml 2>&1 | head -120
' 2>&1
