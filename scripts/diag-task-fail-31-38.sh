#!/usr/bin/env bash
# 219015 排查（二十三）：MCP 工具清单（轮询等待响应版）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
TOKEN=$(S docker exec pf-devkit-mcp printenv FH_SSE_AUTH_TOKEN);
rm -f /tmp/mcp_sse.out
(curl -s -N -m 60 -H 'Accept: text/event-stream' -H "Authorization: Bearer $TOKEN" http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse > /tmp/mcp_sse.out 2>&1 &)
sleep 4
EP=$(grep -oE '/pf-devkit/ai-plugin/mcp/message[^[:space:]]*' /tmp/mcp_sse.out | head -1)
echo "EP=$EP"
ID1="i$(date +%s%N | tail -c 7)"
ID2="l$(date +%s%N | tail -c 7)"
curl -s -m 10 -X POST "http://127.0.0.1:30812$EP" -H 'Content-Type: application/json' -d "{\"jsonrpc\":\"2.0\",\"id\":\"$ID1\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"diag\",\"version\":\"1.0\"}}}" > /dev/null
sleep 1
curl -s -m 10 -X POST "http://127.0.0.1:30812$EP" -H 'Content-Type: application/json' -d "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}" > /dev/null
curl -s -m 15 -X POST "http://127.0.0.1:30812$EP" -H 'Content-Type: application/json' -d "{\"jsonrpc\":\"2.0\",\"id\":\"$ID2\",\"method\":\"tools/list\"}" > /dev/null
for i in $(seq 1 20); do
  if grep -aq 'tools' /tmp/mcp_sse.out; then break; fi
  sleep 1
done
echo '===TOOL_NAMES===';
grep -aoE '"name":"[a-zA-Z0-9_]+"' /tmp/mcp_sse.out | sort -u | head -60;
echo '===PARSE_DESC===';
python3 - <<'PYEOF'
import re, json
raw = open('/tmp/mcp_sse.out', 'r', errors='ignore').read()
m = re.search(r'"tools":\s*\[', raw)
if not m:
    print('no tools array; raw head:')
    print(raw[:600]); raise SystemExit
arr, _ = json.JSONDecoder().raw_decode(raw[m.end()-1:])
for t in arr:
    name = t.get('name','')
    desc = (t.get('description') or '')[:200].replace('\n',' ')
    print(f'## {name} :: {desc}')
PYEOF
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
