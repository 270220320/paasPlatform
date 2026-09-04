#!/usr/bin/env bash
# 诊断限飞区同步鉴权 2：查 pf-safety-service 的 Traefik 路由 labels 与 paasVue demo 的 routeApiBaseUrl
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
  echo "=== pf-safety-service labels ==="
  echo "Tksc@197653" | sudo -S docker inspect pf-safety-service --format "{{json .Config.Labels}}" 2>/dev/null
  echo ""
  echo "=== traefik mounts ==="
  echo "Tksc@197653" | sudo -S docker inspect traefik --format "{{json .Mounts}}" 2>/dev/null
  echo ""
  echo "=== paasjs-demo routeApiBaseUrl in frontend containers ==="
  for c in frontend map-server-nginx lambda-nginx es310-privatization-devops-fe; do
    echo "Tksc@197653" | sudo -S docker exec $c sh -c "grep -r \"routeApiBaseUrl\" -n /usr/share/nginx/html /etc/nginx 2>/dev/null | head -10" 2>/dev/null && echo "--- $c done"
  done
' 2>&1
