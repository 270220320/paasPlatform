#!/usr/bin/env bash
# 2026-09-21 IP 切换修复11：env.map 替换 + 重建所有挂载它的容器
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
echo "===== 1. env.map content (before) ====="
sudo cat /opt/cloudApi2_3.1/data/deploy/env.map
echo "===== 2. backup + sed env.map ====="
sudo cp -a --parents /opt/cloudApi2_3.1/data/deploy/env.map /opt/backup-ip141-20260921/ && echo backed
sudo sed -i 's/192\.168\.76\.141/192.168.76.129/g' /opt/cloudApi2_3.1/data/deploy/env.map && echo replaced
echo "-- after:"
sudo cat /opt/cloudApi2_3.1/data/deploy/env.map
echo "===== 3. containers mounting env.map ====="
CONTS=""
for c in $(sudo docker ps -a --format '{{.Names}}'); do
  if sudo docker inspect "$c" --format '{{range .Mounts}}{{.Source}}{{"\n"}}{{end}}' 2>/dev/null | grep -q "deploy/env.map"; then
    CONTS="$CONTS $c"
    echo "mounts env.map: $c"
  fi
done
echo "===== 4. force-recreate them ====="
if [ -n "$CONTS" ]; then
  cd /opt/cloudApi2_3.1/install && sudo docker compose --env-file /opt/cloudApi2_3.1/fh2-privatization-install-packages/env -f compose.yaml up -d --force-recreate $CONTS 2>&1 | tail -8
fi
sleep 12
echo "===== 5. verify ====="
sudo docker ps -a --format "{{.Names}}\t{{.Status}}" | grep -E "simulator|devkit"
echo "===== 6. global restarting/unhealthy ====="
sudo docker ps --format "{{.Names}}\t{{.Status}}" | grep -iE "restart|unhealthy" || echo "NONE - all stable"
echo "===== FIX11 DONE ====="
REMOTE
