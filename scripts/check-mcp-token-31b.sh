#!/usr/bin/env bash
# 检查3.1 MCP token（二）：带token实测SSE握手
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
T=6b9ff858f79eb09029aa627e6e210b32bcdfdfa44aeb93fb87dd3a73510c990b;
echo '===SSE_WITH_TOKEN===';
curl -s -N -m 4 -H "Authorization: Bearer $T" "http://192.168.76.141:30812/pf-devkit/ai-plugin/mcp/sse" | head -4;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
