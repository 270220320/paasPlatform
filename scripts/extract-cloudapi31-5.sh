#!/usr/bin/env bash
# 司空新包解压（五）：轮询等待解压完成
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===POLLING_EXTRACT===';
for i in $(seq 1 25); do
  sleep 60;
  if pgrep -f "tar -zxf.*5ed7a703" > /dev/null; then
    SZ=$(du -sm /opt/cloudApi2_3.1/ 2>/dev/null | cut -f1);
    echo "[${i}min] running, extracted ${SZ}MB";
  else
    echo "[${i}min] tar process finished";
    break;
  fi
done;
echo '===FINAL_PROC_CHECK===';
pgrep -af "tar -zxf.*5ed7a703" || echo "CONFIRM_NO_TAR";
echo '===FINAL_DIR===';
ls -lh /opt/cloudApi2_3.1/;
echo '===FINAL_SIZE===';
du -sh /opt/cloudApi2_3.1/;
echo '===EXTRACT_LOG===';
cat /home/test/extract_cloudapi31.log;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
