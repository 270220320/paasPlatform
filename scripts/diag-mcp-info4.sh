#!/usr/bin/env bash
# MCP 握手第二轮：打印每次 POST 的状态码与响应体
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
SSH_OPTS="-o StrictHostKeyChecking=no -o ConnectTimeout=8"

setsid -w ssh $SSH_OPTS test@192.168.76.141 bash -s <<'REMOTE'
TOKEN=$(echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp printenv FH_SSE_AUTH_TOKEN 2>/dev/null)
rm -f /tmp/mcp_sse.out
(curl -s -N -m 40 -H 'Accept: text/event-stream' -H "Authorization: Bearer $TOKEN" http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse > /tmp/mcp_sse.out 2>&1 &)
sleep 3
EP=$(grep -oE '/pf-devkit/ai-plugin/mcp/message[^[:space:]]*' /tmp/mcp_sse.out | head -1)
echo "endpoint: $EP"
URL="http://127.0.0.1:30812${EP}"
AUTH="Authorization: Bearer $TOKEN"
echo "--- initialize POST ---"
curl -s -m 8 -w '\nHTTP_CODE=%{http_code}\n' -X POST "$URL" -H 'Content-Type: application/json' -H "$AUTH" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"diag","version":"1.0"}}}'
sleep 2
echo "--- initialized notify ---"
curl -s -m 8 -w '\nHTTP_CODE=%{http_code}\n' -X POST "$URL" -H 'Content-Type: application/json' -H "$AUTH" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
echo "--- tools/list POST ---"
curl -s -m 10 -w '\nHTTP_CODE=%{http_code}\n' -X POST "$URL" -H 'Content-Type: application/json' -H "$AUTH" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
sleep 5
echo "===== SSE stream captured ====="
cat /tmp/mcp_sse.out
REMOTE
