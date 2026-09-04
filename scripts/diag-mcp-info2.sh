#!/usr/bin/env bash
# 查询 pf-devkit-mcp 容器内实际生效配置 + 健康检查实测
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
SSH_OPTS="-o StrictHostKeyChecking=no -o ConnectTimeout=8"

setsid -w ssh $SSH_OPTS test@192.168.76.141 bash -s <<'REMOTE'
mask() { sed -E 's/^([A-Za-z_]*(TOKEN|SECRET|PASSWORD|PASS|KEY)[A-Za-z_]*)=(.{8}).*/\1=\3********(masked)/'; }
echo "===== [6] container runtime env (FH_* / MCP_*, masked) ====="
echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp env 2>/dev/null | grep -E '^(FH_|MCP_|GLIBC)' | sort | mask
echo "===== [7] env.map mcp/devkit section (masked) ====="
echo 'Tksc@197653' | sudo -S grep -iE 'mcp|devkit' /opt/cloudApi2_3/data/deploy/env.map 2>/dev/null | mask
echo "===== [8] run_server.sh ====="
echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp sh -c 'cat run_server.sh 2>/dev/null | head -50' 2>/dev/null
echo "===== [9] health via traefik 30812 ====="
echo "health: $(curl -s -m 5 http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/health)"
echo "ready : $(curl -s -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/ready)"
echo "sse without auth (expect 401): $(curl -s -m 5 -o /dev/null -w '%{http_code}' -H 'Accept: text/event-stream' http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse)"
echo "sse with wrong auth (expect 401): $(curl -s -m 5 -o /dev/null -w '%{http_code}' -H 'Accept: text/event-stream' -H 'Authorization: Bearer wrongtoken' http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse)"
REMOTE
