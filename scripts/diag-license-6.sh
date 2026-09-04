#!/usr/bin/env bash
# 诊断（六）：用远程 python 脚本严格解码 Protected Data（按 Value: 行分组，避免丢字节）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
cat > /tmp/decode_lic.py << "PYEOF"
import sys, re
data = sys.stdin.read()
out = b""
for m in re.finditer(r"Value:\s*((?:0x[0-9a-fA-F]{2}[\s]+)+)", data):
    out += bytes(int(x, 16) for x in m.group(1).split())
sys.stdout.write(out.decode("utf-8", "replace"))
PYEOF
cmu -x -s 130-1620695056 2>/dev/null | python3 /tmp/decode_lic.py;
rm -f /tmp/decode_lic.py;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
