#!/usr/bin/env bash
# 诊断（三）：翻各服务日志找固件上传的真实 URL / 凭证接口路径 / RST 痕迹
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TAS_FIRMWARE_LOGS_2H===;
docker logs tas-service --since 2h 2>&1 | grep -aiE "firmware|ota|upload|sts|presign|credential" | tail -40;
echo ===FILE_STORAGE_LOGS_2H===;
docker logs file-storage --since 2h 2>&1 | grep -aiE "firmware|upload|sts|presign|credential|error|reset|client" | tail -40;
echo ===MINIO_LOGS_2H===;
docker logs minio --since 2h 2>&1 | tail -40;
echo ===TRAEFIK_NONFLEXNET_FIRMWARE_2H===;
docker logs traefik --since 2h 2>&1 | grep -ai "firmware" | grep -av "FlexNet Pre handler" | tail -30;
echo ===TAS_404_5XX_2H===;
docker logs tas-service --since 2h 2>&1 | grep -aE "404|500|502|503|SignatureDoesNotMatch|AccessDenied" | tail -20;
echo ===FILE_STORAGE_RECENT_ERRORS===;
docker logs file-storage --since 2h 2>&1 | grep -aiE "error|warn|fail" | tail -20;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
