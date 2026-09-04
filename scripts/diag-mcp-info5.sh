#!/usr/bin/env bash
# MCP 工具实测（只读）：fh_connect / fh_session_info / fh_ops(status) / fh_device_list
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
SSH_OPTS="-o StrictHostKeyChecking=no -o ConnectTimeout=8"

setsid -w ssh $SSH_OPTS test@192.168.76.141 bash -s <<'REMOTE'
TOKEN=$(echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp printenv FH_SSE_AUTH_TOKEN 2>/dev/null)
rm -f /tmp/mcp_sse.out
(curl -s -N -m 75 -H 'Accept: text/event-stream' -H "Authorization: Bearer $TOKEN" http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse > /tmp/mcp_sse.out 2>&1 &)
sleep 3
EP=$(grep -oE '/pf-devkit/ai-plugin/mcp/message[^[:space:]]*' /tmp/mcp_sse.out | head -1)
URL="http://127.0.0.1:30812${EP}"
AUTH="Authorization: Bearer $TOKEN"
post() { curl -s -m 30 -o /dev/null -X POST "$URL" -H 'Content-Type: application/json' -H "$AUTH" -d "$1"; }
post '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"diag","version":"1.0"}}}'
sleep 1
echo "-- call fh_connect --"
post '{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"fh_connect","arguments":{}}}'
sleep 12
echo "-- call fh_session_info --"
post '{"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"fh_session_info","arguments":{}}}'
sleep 4
echo "-- call fh_ops status --"
post '{"jsonrpc":"2.0","id":12,"method":"tools/call","params":{"name":"fh_ops","arguments":{"action":"status"}}}'
sleep 4
echo "-- call fh_device_list --"
post '{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"name":"fh_device_list","arguments":{"type":"all","online_only":false}}}'
sleep 6
echo "===== SSE stream (message events only) ====="
grep -v '^event:heartbeat' /tmp/mcp_sse.out | sed -n '/event:message/,$p'
echo "===== container log startup (version hints) ====="
echo 'Tksc@197653' | sudo -S docker logs pf-devkit-mcp 2>&1 | grep -iE 'version|sdk|starting' | head -15
REMOTE
