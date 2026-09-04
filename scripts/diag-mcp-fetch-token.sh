#!/usr/bin/env bash
# 从 76.141 compose.yaml 取回 FH_SSE_AUTH_TOKEN，写入本地文件（不在终端回显完整值）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 \
  "echo 'Tksc@197653' | sudo -S grep -oP 'FH_SSE_AUTH_TOKEN: \\K[a-f0-9]+' /opt/cloudApi2_3/install/compose.yaml 2>/dev/null" \
  > /mnt/d/AiFlow/scripts/mcp-sse-token.txt 2>/dev/null
tr -d '\r\n' < /mnt/d/AiFlow/scripts/mcp-sse-token.txt > /tmp/tok.tmp && mv /tmp/tok.tmp /mnt/d/AiFlow/scripts/mcp-sse-token.txt
LEN=$(wc -c < /mnt/d/AiFlow/scripts/mcp-sse-token.txt)
HEAD=$(cut -c1-8 /mnt/d/AiFlow/scripts/mcp-sse-token.txt)
echo "saved: d:\\AiFlow\\scripts\\mcp-sse-token.txt | length=${LEN} | prefix=${HEAD}..."
