#!/usr/bin/env bash
# 诊断限飞区同步鉴权 3：读 Traefik 动态配置找 safety 路由；找 paasjs-demo 静态目录
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
  echo "=== traefik dynamic_conf.yaml (safety/paasjs related) ==="
  echo "Tksc@197653" | sudo -S grep -n -i -E "safety|paasjs|dji2|route" /opt/cloudApi2_3/install/conf/middleware-service/traefik/dynamic_conf.yaml 2>/dev/null | head -60
  echo ""
  echo "=== find paasjs-demo on host fs ==="
  echo "Tksc@197653" | sudo -S find /opt -maxdepth 6 -type d -name "paasjs-demo" 2>/dev/null | head -5
' 2>&1
