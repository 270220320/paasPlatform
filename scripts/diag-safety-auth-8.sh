#!/usr/bin/env bash
# 诊断限飞区同步鉴权 8：看 frontend index.html 的 JS 引用；在可能位置 grep flight-area API 路径
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0

cat > /tmp/remote-cmd.sh <<'EOF'
#!/bin/sh
echo "Tksc@197653" | sudo -S docker ps -q 2>/dev/null > /tmp/cids
for i in $(cat /tmp/cids); do
  name=$(echo "Tksc@197653" | sudo -S docker inspect --format '{{.Name}}' "$i" 2>/dev/null)
  if [ "$name" = "/frontend" ]; then
    echo "=== index.html script refs ==="
    echo "Tksc@197653" | sudo -S docker exec "$i" sh -c 'cat /usr/share/nginx/html/index.html' 2>/dev/null | grep -oE '(src|href)="[^"]+"' | head -20
    echo "=== full fs search flight-area ==="
    echo "Tksc@197653" | sudo -S docker exec "$i" sh -c 'grep -rEoh "[a-z0-9/_.-]*flight-area[a-z0-9/_.-]*" / --include="*.js" 2>/dev/null | sort -u | head -30' 2>/dev/null
  fi
  if [ "$name" = "/es-privatization-system" ] || [ "$name" = "/es-management" ] || [ "$name" = "/es310-privatization-devops-fe" ]; then
    echo "=== $name flight-area refs ==="
    echo "Tksc@197653" | sudo -S docker exec "$i" sh -c 'grep -rEoh "[a-z0-9/_.-]*(flight-area|geofence)[a-z0-9/_.-]*" / 2>/dev/null | sort -u | head -30' 2>/dev/null
  fi
done
EOF

setsid -w scp -o StrictHostKeyChecking=no -q /tmp/remote-cmd.sh test@192.168.76.141:/tmp/remote-cmd.sh 2>/dev/null
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 'sh /tmp/remote-cmd.sh' 2>&1
