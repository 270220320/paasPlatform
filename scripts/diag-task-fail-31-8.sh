#!/usr/bin/env bash
# 3.1任务失败排查（八）：MCP tools/list 提取 fh_flight_task 的 task_type 枚举语义
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 bash -s <<'REMOTE'
TOKEN=$(echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp printenv FH_SSE_AUTH_TOKEN 2>/dev/null)
rm -f /tmp/mcp_sse2.out
(curl -s -N -m 60 -H 'Accept: text/event-stream' -H "Authorization: Bearer $TOKEN" http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse > /tmp/mcp_sse2.out 2>&1 &)
sleep 3
EP=$(grep -oE '/pf-devkit/ai-plugin/mcp/message[^[:space:]]*' /tmp/mcp_sse2.out | head -1)
URL="http://127.0.0.1:30812${EP}"
AUTH="Authorization: Bearer $TOKEN"
curl -s -m 30 -o /dev/null -X POST "$URL" -H 'Content-Type: application/json' -H "$AUTH" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"diag","version":"1.0"}}}'
sleep 1
curl -s -m 30 -o /dev/null -X POST "$URL" -H 'Content-Type: application/json' -H "$AUTH" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
sleep 4
echo '===FH_FLIGHT_TASK_SCHEMA==='
grep -v '^event:heartbeat' /tmp/mcp_sse2.out | sed -n '/event:message/,$p' | grep -oE '"name":"fh_flight_task".*' | head -c 4000
echo;
echo '===DONE==='
REMOTE
