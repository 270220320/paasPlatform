#!/usr/bin/env bash
# 诊断（七）：查 cmu 帮助找完整显示 Protected Data 的选项
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===CMU_HELP_HEAD===;
cmu -h 2>&1 | head -60;
echo ===CMU_HELP_VERBOSE===;
cmu -h 2>&1 | grep -iE "verbose|full|complete|extend|all" | head -20;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
