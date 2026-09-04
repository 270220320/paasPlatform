#!/usr/bin/env bash
# MCP SSE 完整握手：initialize + tools/list，并查 env.map 关键值
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
SSH_OPTS="-o StrictHostKeyChecking=no -o ConnectTimeout=8"

setsid -w ssh $SSH_OPTS test@192.168.76.141 bash -s <<'REMOTE'
echo "===== [10] env.map key values ====="
echo 'Tksc@197653' | sudo -S grep -E '^(TAS_CALLBACK_HOST|ISV_WEBHOOK_URL)=' /opt/cloudApi2_3/data/deploy/env.map 2>/dev/null
TOKEN=$(echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp printenv FH_SSE_AUTH_TOKEN 2>/dev/null)
echo "token len: ${#TOKEN}"
rm -f /tmp/mcp_sse.out
(curl -s -N -m 30 -H 'Accept: text/event-stream' -H "Authorization: Bearer $TOKEN" http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse > /tmp/mcp_sse.out 2>&1 &)
sleep 3
echo "===== [11] SSE first events ====="
head -4 /tmp/mcp_sse.out
EP=$(grep -oE '/pf-devkit/ai-plugin/mcp/message[^[:space:]]*' /tmp/mcp_sse.out | head -1)
echo "endpoint: $EP"
curl -s -m 5 -o /dev/null -X POST "http://127.0.0.1:30812${EP}" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"diag","version":"1.0"}}}'
sleep 1
curl -s -m 5 -o /dev/null -X POST "http://127.0.0.1:30812${EP}" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
curl -s -m 10 -o /dev/null -X POST "http://127.0.0.1:30812${EP}" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
sleep 4
echo "===== [12] SSE stream responses ====="
cat /tmp/mcp_sse.out
REMOTE
