#!/usr/bin/env bash
# 修复验证（六）：机场最近 MQTT 报文方法统计 + index.html 完整 script 列表
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===AIRPORT_METHODS_LAST30MIN===';
SE docker logs tas-service --since 30m 2>&1 | grep -a 'thing/product/8UUXN2K00A0144' | grep -aoE '"method":"[a-z_]+"' | sort | uniq -c | sort -rn | head -15;
echo '===AIRPORT_TOPIC_LAST30MIN===';
SE docker logs tas-service --since 30m 2>&1 | grep -a '8UUXN2K00A0144' | grep -aoE 'topic[":= ]+thing/product/8UUXN2K00A0144/[a-z_]+' | sort | uniq -c | sort -rn | head -10;
echo '===ALL_SCRIPTS_IN_INDEX===';
SE bash -c 'grep -oE "<script[^>]*>" /tmp/idx.html 2>/dev/null | head -15';
echo '===INDEX_MODULE_SRC===';
SE bash -c 'grep -oE "import\(|from \"[^\"]+\"|\"/[^\"]+\.js\"" /tmp/idx.html 2>/dev/null | head -15';
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
