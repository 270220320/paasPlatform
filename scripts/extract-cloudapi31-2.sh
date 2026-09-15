#!/usr/bin/env bash
# 司空新包解压（二）：确认目标目录状态 + 包顶层结构
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===TARGET_DIR_CONTENT===';
echo 'Tksc@197653' | sudo -S ls -la /opt/cloudApi2_3.1/ 2>/dev/null;
echo '===OLD_DIR_STRUCTURE===';
ls /opt/cloudApi2_3/;
echo '===PKG_TOP_ENTRIES===';
tar -tzf /home/test/Downloads/5ed7a703-6a13-4688-9de9-d9c3ce054c34.tar.gz 2>/dev/null | head -25;
echo '===PKG_CHECKSUM_SIZE===';
ls -l /home/test/Downloads/5ed7a703-6a13-4688-9de9-d9c3ce054c34.tar.gz;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
