#!/usr/bin/env bash
# 找私有化司空(76.141)上 dji2 task REST 的真实路径（sudo bash -c + base64）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo "=== A) frontend JS: *.rest paths ==="
docker exec frontend sh -c 'grep -rhoaE "[^\"'"'"' ]{5,90}\.rest" /usr/share/nginx/html/static/js/ 2>/dev/null | sort -u | head -40'
echo "=== B) frontend JS: routeApiBaseUrl ==="
docker exec frontend sh -c 'grep -rhoaE "routeApiBaseUrl.{0,100}" /usr/share/nginx/html/ 2>/dev/null | sort -u | head -10'
echo "=== C) task-ish containers ==="
docker ps --format '{{.Names}} -> {{.Ports}}' | grep -aiE 'task|tas|route|dji2|flight' | head -15
echo "=== E) traefik config: task routes ==="
docker exec traefik sh -c 'grep -rhoaE "/(task|flighttask)[a-zA-Z/._-]{0,50}" /etc/traefik/ 2>/dev/null | sort -u | head -30'
echo DONE
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo Tksc@197653 | sudo -S bash -c 'echo $R | base64 -d | bash'"
