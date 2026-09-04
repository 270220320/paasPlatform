#!/usr/bin/env bash
# 诊断（五）：解码 CodeMeter 许可证 Extended Protected Data 中的 JSON（验证 uuid 是否为 a12f2ee2-...）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===DECODE_PROTECTED_DATA===;
cmu -x -s 130-1620695056 2>/dev/null | grep -oP "\b0x[0-9a-f]{2}\b" | python3 -c "import sys;print(bytes(int(x,16) for x in sys.stdin.read().split()).decode(\"utf-8\",\"replace\"))";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
