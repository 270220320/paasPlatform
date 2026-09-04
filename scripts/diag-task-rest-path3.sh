#!/usr/bin/env bash
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo "=== A) frontend html dir ==="
docker exec frontend sh -c 'ls /usr/share/nginx/html/ 2>/dev/null | head; ls /usr/share/nginx/html/static 2>/dev/null | head -5; find /usr/share/nginx/html -name "*.js" 2>/dev/null | head -5'
echo "=== B) frontend nginx conf: task-management location ==="
docker exec frontend sh -c 'grep -ra task /etc/nginx/ 2>/dev/null | head -20'
echo "=== C) traefik access log: task POSTs (7d) ==="
docker logs traefik --since 168h 2>&1 | grep -aiE '"POST[^"]*task' | tail -15
echo "=== D) es-task recent logs ==="
docker logs es-task --since 72h 2>&1 | tail -20
echo DONE
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo Tksc@197653 | sudo -S bash -c 'echo $R | base64 -d | bash'"
