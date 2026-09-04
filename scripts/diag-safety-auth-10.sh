#!/usr/bin/env bash
# 诊断限飞区同步鉴权 10：提取 pf-safety-server 的 HTTP 路由字符串（area 相关）
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
  if [ "$name" = "/pf-safety-service" ]; then
    echo "=== http route strings containing area ==="
    echo "Tksc@197653" | sudo -S docker exec "$i" sh -c "grep -aoE '/?[a-z0-9:_-]*area[a-z0-9:_-]*' /app/server 2>/dev/null | grep -aE '^/|v1' | sort -u | head -50" 2>/dev/null
    echo "=== all api-like route strings ==="
    echo "Tksc@197653" | sudo -S docker exec "$i" sh -c "grep -aoE '(GET|POST|PUT|DELETE) +/[a-zA-Z0-9/_:-]+' /app/server 2>/dev/null | sort -u | head -40; grep -aoE '\"/[a-z0-9:_-]+/[a-z0-9:_-]+[a-zA-Z0-9/_:-]*\"' /app/server 2>/dev/null | sort -u | head -60" 2>/dev/null
  fi
done
EOF

setsid -w scp -o StrictHostKeyChecking=no -q /tmp/remote-cmd.sh test@192.168.76.141:/tmp/remote-cmd.sh 2>/dev/null
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 'sh /tmp/remote-cmd.sh' 2>&1
