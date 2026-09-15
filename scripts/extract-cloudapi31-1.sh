#!/usr/bin/env bash
# 司空新包解压（一）：探测 /home/test/Downloads 新包与 /opt 现状
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===DOWNLOADS_LIST===';
ls -lht /home/test/Downloads | head -30;
echo '===OPT_DIR===';
ls -l /opt/ | grep -i cloud;
echo '===DISK===';
df -h /opt;
echo '===WHOAMI===';
whoami;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
