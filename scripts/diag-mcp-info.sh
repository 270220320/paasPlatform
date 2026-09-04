#!/usr/bin/env bash
# 查询 76.141 上 pf-devkit-mcp 的接入信息（一次性诊断脚本）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
SSH_OPTS="-o StrictHostKeyChecking=no -o ConnectTimeout=8"

setsid -w ssh $SSH_OPTS test@192.168.76.141 bash -s <<'REMOTE'
echo "===== [1] traefik command ====="
echo 'Tksc@197653' | sudo -S docker inspect traefik --format '{{json .Config.Cmd}}' 2>/dev/null
echo "===== [2] traefik mounts ====="
echo 'Tksc@197653' | sudo -S docker inspect traefik --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}' 2>/dev/null
echo "===== [3] pf-devkit-mcp service in compose.yaml ====="
echo 'Tksc@197653' | sudo -S sed -n '/pf-devkit-mcp:/,/^  [a-z]/p' /opt/cloudApi2_3/install/compose.yaml 2>/dev/null | head -70
echo "===== [4] env file (sensitive masked) ====="
for f in /opt/cloudApi2_3/install/.env /opt/cloudApi2_3/install/env /opt/cloudApi2_3/install/.env.local; do
  if [ -f "$f" ]; then
    echo "--- $f ---"
    echo 'Tksc@197653' | sudo -S grep -E '^(FH_|MCP|GLIBC)' "$f" 2>/dev/null | sed -E 's/^([A-Z_]*(TOKEN|SECRET|PASSWORD|PASS|KEY)[A-Z_]*)=(.{6}).*/\1=\3****MASKED****/'
  fi
done
echo "===== [5] find env files under /opt/cloudApi2_3 ====="
echo 'Tksc@197653' | sudo -S find /opt/cloudApi2_3 -maxdepth 3 \( -name '*.env*' -o -name 'env' \) 2>/dev/null | head -20
REMOTE
