#!/usr/bin/env bash
# 媒体清理侦察（四）：remain_upload 上下文 + 82d606ec 上传流量（traefik/MinIO）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===REMAIN_UPLOAD_CONTEXT===;
docker logs tas-service --since 12m 2>&1 | grep -a "remain_upload" | tail -2;
echo ===TRAEFIK_82d6_TRAFFIC===;
docker logs traefik --since 30m 2>&1 | grep -a "82d606ec" | tail -8;
echo ===TRAEFIK_UPLOAD_PUTS===;
docker logs traefik --since 30m 2>&1 | grep -aE "PUT|POST" | grep -a "file-storage-privatization" | tail -8;
echo ===MINIO_82d6_LATEST===;
docker exec minio sh -c "ls -lat /data/file-storage-privatization/82d606ec10d02a884c51e2719dfe2bb3/ | head";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
