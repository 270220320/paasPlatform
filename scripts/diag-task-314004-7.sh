#!/usr/bin/env bash
# 诊断（七）：GNSS 任务窗内 pf-control-core / drtc-manager / es-drc-service 的 SN 相关日志
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
W1=2026-08-25T01:42:40; W2=2026-08-25T01:43:35;
for S in pf-control-core drtc-manager es-drc-service; do
  echo "====$S====";
  docker logs --since $W1 --until $W2 $S 2>&1 | grep -a 8UUXN2K00A0144 | grep -avE "osd|hms|china_oid" | tail -15;
done;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
