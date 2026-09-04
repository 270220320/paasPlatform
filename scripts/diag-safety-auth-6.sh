#!/usr/bin/env bash
# 诊断限飞区同步鉴权 6：列出全部容器 traefik 路由规则（rule + entryPoints）与所属容器名
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0

cat > /tmp/remote-cmd.sh <<'EOF'
#!/bin/sh
echo "Tksc@197653" | sudo -S docker ps -q 2>/dev/null > /tmp/cids
while read i; do
  echo "Tksc@197653" | sudo -S docker inspect "$i" 2>/dev/null
done < /tmp/cids | grep -E '"Name": "/|"traefik\..*(\.rule|entryPoints|\.service)"' | grep -v '"com\.'
EOF

setsid -w scp -o StrictHostKeyChecking=no -q /tmp/remote-cmd.sh test@192.168.76.141:/tmp/remote-cmd.sh 2>/dev/null
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 'sh /tmp/remote-cmd.sh' 2>&1
