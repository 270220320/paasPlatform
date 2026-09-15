#!/usr/bin/env bash
# 219015 排查（二十五）：MCP 工具清单（解析 SSE 流）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
TOKEN=$(S docker exec pf-devkit-mcp printenv FH_SSE_AUTH_TOKEN);
rm -f /tmp/mcp_sse2.out
(curl -s -N -m 40 -H 'Accept: text/event-stream' -H "Authorization: Bearer $TOKEN" http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse > /tmp/mcp_sse2.out 2>&1 &)
sleep 4
EP=$(grep -oE '/pf-devkit/ai-plugin/mcp/message[^[:space:]]*' /tmp/mcp_sse2.out | head -1)
ID1="i$(date +%s%N | tail -c 7)"
ID2="l$(date +%s%N | tail -c 7)"
curl -s -m 10 -X POST "http://127.0.0.1:30812$EP" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d "{\"jsonrpc\":\"2.0\",\"id\":\"$ID1\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"diag\",\"version\":\"1.0\"}}" > /dev/null
sleep 1
curl -s -m 10 -X POST "http://127.0.0.1:30812$EP" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}" > /dev/null
sleep 1
curl -s -m 25 -X POST "http://127.0.0.1:30812$EP" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d "{\"jsonrpc\":\"2.0\",\"id\":\"$ID2\",\"method\":\"tools/list\"}" > /dev/null
for i in $(seq 1 25); do
  if grep -aq '"result":.*"tools"' /tmp/mcp_sse2.out; then break; fi
  sleep 1
done
echo '===ALL_TOOLS===';
python3 - <<'PYEOF'
import json, re
raw = open('/tmp/mcp_sse2.out', 'r', errors='ignore').read()
tools = []
for m in re.finditer(r'^data:(\{.*\})\s*$', raw, re.M):
    line = m.group(1)
    try:
        d = json.loads(line)
    except Exception:
        continue
    r = d.get('result') or {}
    ts = r.get('tools')
    if ts: tools = ts
for t in tools:
    name = t.get('name','')
    desc = (t.get('description') or '')[:260].replace('\n',' ')
    print(f'## {name} :: {desc}')
PYEOF
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
