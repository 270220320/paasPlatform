#!/usr/bin/env bash
# 诊断限飞区同步鉴权 5：列出全部容器的 traefik 路由规则，找 safety 与 gateway 入口
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 '
  echo "=== all traefik router rules ==="
  echo "Tksc@197653" | sudo -S docker ps -q 2>/dev/null | xargs sudo docker inspect --format "{{.Name}}|||{{range \$k,\$v := .Config.Labels}}{{\$k}}={{\$v}};{{end}}" 2>/dev/null \
    | tr ";" "\n" | grep -E "routers\.[a-z0-9-]+\.rule|^\S+?\|\|\|" | sed "s/com.docker.compose.*//;s/org.opencontainers.*//" | grep -v "^\S*-$" | head -80
  echo ""
  echo "=== entrypoints in traefik static conf ==="
  echo "Tksc@197653" | sudo -S docker inspect traefik --format "{{json .Config.Cmd}}" 2>/dev/null
  echo "Tksc@197653" | sudo -S docker exec traefik sh -c "cat /etc/traefik/traefik.yml 2>/dev/null || cat /etc/traefik/traefik.toml 2>/dev/null" 2>/dev/null | head -40
' 2>&1
