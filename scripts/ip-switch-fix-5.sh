#!/usr/bin/env bash
# 2026-09-21 IP 切换修复5：排查 forbid-service 证书损坏
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
echo "===== 1. forbid-service volumes ====="
sudo docker inspect forbid-service --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}'
echo "===== 2. compose.yaml forbid-service section ====="
sudo grep -n -B2 -A25 "container_name: forbid-service" /opt/cloudApi2_3.1/install/compose.yaml | head -45
echo "===== 3. cert source files on host ====="
for f in $(sudo docker inspect forbid-service --format '{{range .Mounts}}{{println .Source}}{{end}}' | grep -iE "cert|ssl|tls" | grep "^/"); do
  echo "--- $f ---"
  sudo ls -la "$f" 2>/dev/null
  sudo head -2 "$f" 2>/dev/null
done
echo "===== 4. cert-related services (who generates) ====="
sudo grep -n "cert" /opt/cloudApi2_3.1/install/compose.yaml | grep -viE "\.json|dashboards" | head -15
echo "===== FIX5 DONE ====="
REMOTE
