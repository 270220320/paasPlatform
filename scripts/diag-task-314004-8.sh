#!/usr/bin/env bash
# 诊断（八）：file-storage/minio 在任务窗内是否收到 kmz 下载请求（机场网络连通性验证）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
W1=2026-08-25T01:42:40; W2=2026-08-25T01:43:40;
echo ===FILE_STORAGE_KMZ===;
docker logs --since $W1 --until $W2 file-storage 2>&1 | grep -aiE "kmz|719d6171|GET" | tail -20;
echo ===MINIO_KMZ===;
docker logs --since $W1 --until $W2 minio 2>&1 | grep -aiE "kmz|719d6171" | tail -10;
echo ===FILE_STORAGE_ALL_TAIL===;
docker logs --since $W1 --until $W2 file-storage 2>&1 | tail -10;
echo ===PORT_30802===;
docker ps --format "{{.Names}} {{.Ports}}" | grep -a 30802;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
