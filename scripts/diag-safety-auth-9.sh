#!/usr/bin/env bash
# 诊断限飞区同步鉴权 9：从 pf-safety-service 二进制提取 API 子路径
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
    echo "=== pf-safety-service entrypoint & workdir ==="
    echo "Tksc@197653" | sudo -S docker inspect --format '{{.Path}} {{.Config.WorkingDir}} {{json .Config.Cmd}}' "$i" 2>/dev/null
    echo "=== strings in binary: api paths ==="
    echo "Tksc@197653" | sudo -S docker exec "$i" sh -c 'ls /; BIN=$(ls /app 2>/dev/null | head -5); echo "$BIN"' 2>/dev/null
    echo "=== grep route paths ==="
    echo "Tksc@197653" | sudo -S docker exec "$i" sh -c 'grep -aoE "/(api|manage|v1)[a-zA-Z0-9/_.:-]*" /app/* 2>/dev/null | grep -iE "flight|area|geofence|nfz" | sort -u | head -40' 2>/dev/null
  fi
done
EOF

setsid -w scp -o StrictHostKeyChecking=no -q /tmp/remote-cmd.sh test@192.168.76.141:/tmp/remote-cmd.sh 2>/dev/null
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 'sh /tmp/remote-cmd.sh' 2>&1
