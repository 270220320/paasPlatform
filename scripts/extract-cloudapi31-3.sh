#!/usr/bin/env bash
# 司空新包解压（三）：后台启动解压 64G 包到 /opt/cloudApi2_3.1/
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===CHECK_EXISTING_TAR===';
pgrep -af "tar.*5ed7a703" || echo "no_running_tar";
echo '===PIGZ_AVAIL===';
command -v pigz || echo "no_pigz";
echo '===START_EXTRACT===';
echo 'Tksc@197653' | sudo -S bash -c 'nohup tar -zxf /home/test/Downloads/5ed7a703-6a13-4688-9de9-d9c3ce054c34.tar.gz -C /opt/cloudApi2_3.1/ > /home/test/extract_cloudapi31.log 2>&1 & echo STARTED_PID=$!';
sleep 3;
echo '===AFTER_3S===';
pgrep -af "tar.*5ed7a703" || echo "tar_not_running_anymore";
ls -la /opt/cloudApi2_3.1/;
cat /home/test/extract_cloudapi31.log;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
