#!/usr/bin/env bash
# 219015 排查（二十二）：MCP 全工具清单（找设备绑定工具）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
TOKEN=$(S docker exec pf-devkit-mcp printenv FH_SSE_AUTH_TOKEN);
rm -f /tmp/mcp_sse.out
(curl -s -N -m 25 -H 'Accept: text/event-stream' -H "Authorization: Bearer $TOKEN" http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse > /tmp/mcp_sse.out 2>&1 &)
sleep 3
EP=$(grep -oE '/pf-devkit/ai-plugin/mcp/message[^[:space:]]*' /tmp/mcp_sse.out | head -1)
echo "EP=$EP"
ID1="init-$(date +%s%N | tail -c 8)"
ID2="list-$(date +%s%N | tail -c 8)"
curl -s -m 8 -X POST "http://127.0.0.1:30812$EP" -H 'Content-Type: application/json' -d "{\"jsonrpc\":\"2.0\",\"id\":\"$ID1\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"diag\",\"version\":\"1.0\"}}}" > /dev/null
curl -s -m 8 -X POST "http://127.0.0.1:30812$EP" -H 'Content-Type: application/json' -d "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}" > /dev/null
curl -s -m 10 -X POST "http://127.0.0.1:30812$EP" -H 'Content-Type: application/json' -d "{\"jsonrpc\":\"2.0\",\"id\":\"$ID2\",\"method\":\"tools/list\"}" > /dev/null
sleep 2
echo '===TOOL_NAMES===';
grep -aoE '"name":"fh_[a-z0-9_]+"' /tmp/mcp_sse.out | sort -u;
echo '===BIND_RELATED===';
grep -aoiE '"name":"[a-z0-9_]*bind[a-z0-9_]*"' /tmp/mcp_sse.out | sort -u;
echo '===DEVICE_TOOL_DESC===';
python3 - <<'PYEOF'
import re
raw = open('/tmp/mcp_sse.out', 'r', errors='ignore').read()
# 找 tools/list 响应里的 tools 数组
m = re.search(r'"tools":\s*\[', raw)
if not m:
    print('no tools array found'); raise SystemExit
dec = json.JSONDecoder()
# 从 [ 后开始解析 JSON 数组
import json
arr_txt = raw[m.end()-1:]
arr, _ = dec.raw_decode(arr_txt)
for t in arr:
    name = t.get('name','')
    desc = (t.get('description') or '')[:220].replace('\n',' ')
    print(f'## {name}\n   {desc}')
PYEOF
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
