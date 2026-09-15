#!/usr/bin/env bash
# 219015 排查（二十四）：MCP 工具清单（POST 直接响应版）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
TOKEN=$(S docker exec pf-devkit-mcp printenv FH_SSE_AUTH_TOKEN);
rm -f /tmp/mcp_sse.out /tmp/mcp_post*.json
(curl -s -N -m 90 -H 'Accept: text/event-stream' -H "Authorization: Bearer $TOKEN" http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse > /tmp/mcp_sse.out 2>&1 &)
sleep 4
EP=$(grep -oE '/pf-devkit/ai-plugin/mcp/message[^[:space:]]*' /tmp/mcp_sse.out | head -1)
echo "EP=$EP"
ID1="i$(date +%s%N | tail -c 7)"
ID2="l$(date +%s%N | tail -c 7)"
echo "--- init POST resp:";
curl -s -m 10 -w '\nHTTP=%{http_code}\n' -X POST "http://127.0.0.1:30812$EP" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d "{\"jsonrpc\":\"2.0\",\"id\":\"$ID1\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"diag\",\"version\":\"1.0\"}}" | head -c 800
echo; echo "--- tools/list POST resp:";
curl -s -m 20 -w '\nHTTP=%{http_code}\n' -X POST "http://127.0.0.1:30812$EP" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d "{\"jsonrpc\":\"2.0\",\"id\":\"$ID2\",\"method\":\"tools/list\"}" > /tmp/mcp_tools.json
head -c 400 /tmp/mcp_tools.json; echo
echo '===TOOLS_FROM_POST===';
python3 - <<'PYEOF'
import json
try:
    d = json.load(open('/tmp/mcp_tools.json'))
except Exception as e:
    print('parse err', e); raise SystemExit
res = d.get('result') or {}
for t in res.get('tools', []):
    name = t.get('name','')
    desc = (t.get('description') or '')[:180].replace('\n',' ')
    print(f'## {name} :: {desc}')
PYEOF
echo '===SSE_TAIL===';
tail -5 /tmp/mcp_sse.out | cut -c1-300;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
