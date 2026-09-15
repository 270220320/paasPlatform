#!/usr/bin/env bash
# 修复验证（十二）：找 CreateService HTTP 路径 + drone_power_on 指令存在性 + MCP 工具 schema
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_TAS_API_PATHS_0303_WINDOW===';
SE docker logs tas-service --since 3h 2>&1 | grep -a 'label":"API' | grep -ao '"path":"[^"]*"' | sort | uniq -c | sort -rn | head -20;
echo '===B_TAS_API_030323_CREATE===';
SE docker logs tas-service --since 3h 2>&1 | grep -a '03:03:23' | grep -ai 'API' | cut -c1-700 | grep -aiE 'service|command|create' | head -5;
echo '===C_TAS_BIN_POWERON_SUBSTR===';
S docker exec tas-service sh -c "tr -c '[:print:]' '\n' < /app/main 2>/dev/null | grep -aE 'drone_power|cover_open|cover_close|power_on|remote_debug|debug_command' | sort -u | head -25" 2>/dev/null;
echo '===D_EMQX_TOPIC_SERVICES===';
S docker exec tas-service sh -c "tr -c '[:print:]' '\n' < /app/main 2>/dev/null | grep -aE 'thing/product/%s/services|/services\$|services_reply' | sort -u | head -8" 2>/dev/null;
echo '===E_MCP_TOOLS_SCHEMA===';
TOK=$(S docker exec pf-devkit-mcp printenv FH_SSE_AUTH_TOKEN 2>/dev/null | tr -d '\r\n');
S docker logs pf-devkit-mcp --since 30m >/dev/null 2>&1;
curl -s -m 8 -N -X POST http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -H 'Accept: text/event-stream' -d '{"jsonrpc":"2.0","id":31,"method":"tools/list"}' -o /tmp/sse31.txt 2>/dev/null || true;
sleep 4;
grep -ao 'data: {.*' /tmp/sse31.txt 2>/dev/null | tail -1 > /tmp/sse31data.txt;
wc -c /tmp/sse31data.txt 2>/dev/null;
python3 - <<'PYEOF' 2>/dev/null || echo PY_FAIL
import json
raw = open('/tmp/sse31data.txt').read().strip()
if raw.startswith('data: '): raw = raw[6:]
j = json.loads(raw)
tools = j.get('result', {}).get('tools', [])
for t in tools:
    n = t.get('name','')
    if any(k in n for k in ('drone','device','flight','control','dock','power')):
        desc = (t.get('description','') or '')[:150].replace('\n',' ')
        print(n, '|', desc)
print('TOTAL', len(tools))
PYEOF
echo '===F_ESTASK_CREATE_SERVICE_CALL===';
SE docker logs es-task --since 3h 2>&1 | grep -a 'CreateService' | grep -ao 'http://[^"]*' | sort -u | head -5;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
