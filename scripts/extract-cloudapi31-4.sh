#!/usr/bin/env bash
# 司空新包解压（四）：清理残留探测进程 + 检查解压进度
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===KILL_STALE_TZ===';
echo 'Tksc@197653' | sudo -S kill 2505972 2>/dev/null; echo "kill_rc=$?";
echo '===EXTRACT_PROC===';
pgrep -af "tar -zxf.*5ed7a703" || echo "EXTRACT_FINISHED_OR_DEAD";
echo '===CURRENT_SIZE===';
echo 'Tksc@197653' | sudo -S du -sh /opt/cloudApi2_3.1/ 2>/dev/null;
echo '===CURRENT_FILES===';
echo 'Tksc@197653' | sudo -S ls -lh /opt/cloudApi2_3.1/ 2>/dev/null;
echo '===LOG===';
cat /home/test/extract_cloudapi31.log;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
