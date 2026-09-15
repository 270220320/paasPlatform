#!/usr/bin/env bash
# 3.1任务失败排查（九）：MCP容器内SDK jar的execute方法 + atc execute路由
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===MCP_SDK_JARS===';
echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp find / -name '*flight*hub*' -o -name '*flighthub*' 2>/dev/null | grep -E '\.jar|\.so' | head -10;
echo '===ATC_ROUTE_EXECUTE===';
echo 'Tksc@197653' | sudo -S docker logs tas-service --since 48h 2>&1 | grep -aiE 'flight.?tasks.*/execute|/executions|action.*execute' | tail -6;
echo '===ATC_SWAGGER===';
curl -s -m 8 -H 'auth_keys: user_id' -H 'user_id: paas_test' "http://192.168.76.141:30812/gateway/atc/v3/api-docs" 2>/dev/null | grep -oE '"/[^"]*(execute|flight-tasks)[^"]*"\s*:\s*\{' | head -10;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
