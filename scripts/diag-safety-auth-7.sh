#!/usr/bin/env bash
# 诊断限飞区同步鉴权 7：在司空前端容器 JS 中找飞行安全区真实 API 路径
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0

cat > /tmp/remote-cmd.sh <<'EOF'
#!/bin/sh
echo "Tksc@197653" | sudo -S docker ps -q 2>/dev/null > /tmp/cids
# 找 frontend 容器 id
for i in $(cat /tmp/cids); do
  name=$(echo "Tksc@197653" | sudo -S docker inspect --format '{{.Name}}' "$i" 2>/dev/null)
  if [ "$name" = "/frontend" ]; then
    echo "=== frontend container js dir ==="
    echo "Tksc@197653" | sudo -S docker exec "$i" sh -c 'ls /usr/share/nginx/html 2>/dev/null | head; grep -rEoh "[a-z0-9/_-]*flight-area[a-z0-9/_-]*" /usr/share/nginx/html 2>/dev/null | sort -u | head -40' 2>/dev/null
  fi
done
EOF

setsid -w scp -o StrictHostKeyChecking=no -q /tmp/remote-cmd.sh test@192.168.76.141:/tmp/remote-cmd.sh 2>/dev/null
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 'sh /tmp/remote-cmd.sh' 2>&1
