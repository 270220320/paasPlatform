#!/usr/bin/env bash
# 修复验证（十一）：确认无人机未上线 + 可用的开机指令（tas identifier 字典）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_STATUS_RAW_24H===';
SE docker logs tas-service --since 24h 2>&1 | grep -a '"topic":"sys/product/8UUXN2K00A0144/status"' | cut -c1-900;
echo '===B_ONLINE_MSG_LINES_24H===';
SE docker logs tas-service --since 24h 2>&1 | grep -a 'online Message' | cut -c1-500;
echo '===C_DRONE_SN_CONTEXT_SAMPLE===';
SE docker logs tas-service --since 24h 2>&1 | grep -a '1581F8HGX252500A009X' | grep -av 'osd' | tail -6 | cut -c1-450;
echo '===D_TAS_SERVICE_IDENTIFIERS===';
S docker exec tas-service sh -c "tr -c '[:print:]' '\n' < /app/main 2>/dev/null | grep -axE '[a-z0-9_]+' | grep -aE 'power_on|cover_|charge|escape|reboot|drone_' | sort -u | head -30" 2>/dev/null;
echo '===E_ESTASK_IDENTIFIERS===';
S docker exec es-task sh -c "tr -c '[:print:]' '\n' < /app/http_main 2>/dev/null | grep -axE '[a-z0-9_]+' | grep -aE 'power_on|cover_open|airport_|drone_open' | sort -u | head -20" 2>/dev/null;
echo '===F_MCP_DRONE_CONTROL_SCHEMA===';
TOK=$(S docker exec pf-devkit-mcp printenv FH_SSE_AUTH_TOKEN 2>/dev/null | tr -d '\r\n');
curl -s -m 6 -X POST http://127.0.0.1:30812/pf-devkit/ai-plugin/mcp/sse -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":21,"method":"tools/list"}' >/dev/null 2>&1;
sleep 3;
S docker logs pf-devkit-mcp --since 2m 2>&1 | grep -ao 'fh_[a-z_]*' | sort -u | head;
echo '===G_DEVICES_TABLE_RETRY===';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -e 'SELECT * FROM ThingAccessService.devices \G' " 2>/dev/null | head -40;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
