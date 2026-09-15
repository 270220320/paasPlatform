#!/usr/bin/env bash
# 检查3.1 MCP token是否变化：对比新旧compose + 容器env + 实测鉴权
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===NEW_31_TOKEN===';
echo 'Tksc@197653' | sudo -S awk '/^  pf-devkit-mcp:/{f=1} f&&/^  [a-z]/{if(!/pf-devkit-mcp/)exit} f' /opt/cloudApi2_3.1/install/compose.yaml 2>/dev/null | grep -iE 'TOKEN|SECURITY|TRANSPORT' | sed 's/^ *//';
echo '===OLD_30_TOKEN===';
echo 'Tksc@197653' | sudo -S awk '/^  pf-devkit-mcp:/{f=1} f&&/^  [a-z]/{if(!/pf-devkit-mcp/)exit} f' /opt/cloudApi2_3/install/compose.yaml 2>/dev/null | grep -iE 'TOKEN|SECURITY|TRANSPORT' | sed 's/^ *//';
echo '===CONTAINER_ENV===';
echo 'Tksc@197653' | sudo -S docker exec pf-devkit-mcp env 2>/dev/null | grep -iE 'TOKEN|SECURITY_LEVEL|TRANSPORT';
echo '===MCP_HEALTH===';
curl -s -m 8 http://192.168.76.141:30812/pf-devkit/ai-plugin/mcp/health;
echo;
echo '===TEST_AUTH_NO_TOKEN===';
code1=$(curl -s -o /dev/null -w '%{http_code}' -m 8 "http://192.168.76.141:30812/pf-devkit/ai-plugin/mcp/sse");
echo "no_token_code=$code1";
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
