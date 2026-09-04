#!/usr/bin/env bash
# 用 dry_run 实测 FH_MAX_SECURITY_LEVEL=2 下各等级工具的实际准入行为（不执行任何真实操作）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
SSH_OPTS="-o StrictHostKeyChecking=no -o ConnectTimeout=8"

setsid -w ssh $SSH_OPTS test@192.168.76.141 bash -s <<'REMOTE'
TOKEN=$(echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp printenv FH_SSE_AUTH_TOKEN 2>/dev/null)
DRONE="1581F8HGX252500A009X"
DOCK="8UUXN2K00A0144"
rm -f /tmp/mcp_sse.out
(curl -s -N -m 75 -H 'Accept: text/event-stream' -H "Authorization: Bearer $TOKEN" http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse > /tmp/mcp_sse.out 2>&1 &)
sleep 3
EP=$(grep -oE '/pf-devkit/ai-plugin/mcp/message[^[:space:]]*' /tmp/mcp_sse.out | head -1)
URL="http://127.0.0.1:30812${EP}"
AUTH="Authorization: Bearer $TOKEN"
post() { curl -s -m 30 -o /dev/null -X POST "$URL" -H 'Content-Type: application/json' -H "$AUTH" -d "$1"; }
post '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"diag","version":"1.0"}}}'
sleep 1
post '{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"fh_connect","arguments":{}}}'
sleep 12
echo "-- [A] fh_takeoff dry_run (官方文档归 L3, 矩阵标 L2) --"
post "{\"jsonrpc\":\"2.0\",\"id\":20,\"method\":\"tools/call\",\"params\":{\"name\":\"fh_takeoff\",\"arguments\":{\"drone_sn\":\"$DRONE\",\"dock_sn\":\"$DOCK\",\"target_height\":50,\"confirm\":true,\"dry_run\":true}}}"
sleep 4
echo "-- [B] fh_emergency_stop dry_run (L3, 预期被拒) --"
post "{\"jsonrpc\":\"2.0\",\"id\":21,\"method\":\"tools/call\",\"params\":{\"name\":\"fh_emergency_stop\",\"arguments\":{\"drone_sn\":\"$DRONE\",\"confirm\":true,\"reason\":\"seclvl matrix validation dry-run\",\"dry_run\":true}}}"
sleep 4
echo "-- [C] fh_flyto dry_run (L2 对照) --"
post "{\"jsonrpc\":\"2.0\",\"id\":22,\"method\":\"tools/call\",\"params\":{\"name\":\"fh_flyto\",\"arguments\":{\"drone_sn\":\"$DRONE\",\"action\":\"start\",\"latitude\":39.9,\"longitude\":117.4,\"height\":30,\"confirm\":true,\"dry_run\":true}}}"
sleep 4
echo "-- [D] fh_dock_control dry_run (L1 对照) --"
post "{\"jsonrpc\":\"2.0\",\"id\":23,\"method\":\"tools/call\",\"params\":{\"name\":\"fh_dock_control\",\"arguments\":{\"dock_sn\":\"$DOCK\",\"action\":\"cover_open\",\"confirm\":true,\"dry_run\":true}}}"
sleep 5
echo "===== SSE message events ====="
grep -A1 '^event:message' /tmp/mcp_sse.out | grep '^data:' | grep -oE '"id":(10|20|21|22|23),.*' | head -10
echo ""
echo "===== raw responses (id 20-23) ====="
grep -A1 '^event:message' /tmp/mcp_sse.out | grep '^data:' | grep -E '"id":(2[0-3])'
REMOTE
