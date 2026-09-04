#!/usr/bin/env bash
# 诊断（六）：在 device-manager-sdk-v2 与 upload-master JS 中挖固件上传 API/URL 拼装逻辑
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===DM_SDK_DIR_LIST===;
docker exec frontend sh -c "find /app/paasjs-demo/device-manager-sdk-v2 -maxdepth 2 | head -30";
echo ===DM_SDK_FIRMWARE_API===;
docker exec frontend sh -c "grep -rohE \"/[a-zA-Z0-9_/{}.-]*(firmware|firmwares|ota)[a-zA-Z0-9_/{}.-]*\" /app/paasjs-demo/device-manager-sdk-v2 2>/dev/null | sort | uniq -c | sort -rn | head -30";
echo ===DM_SDK_UPLOAD_URL_PATTERNS===;
docker exec frontend sh -c "grep -rohE \"(https?://|\\\"|endPoint|endpoint|bucket|Endpoint)[^\\\"]{0,80}(9000|30802|minio|Minio|file-storage)[^\\\"]{0,60}\" /app/paasjs-demo/device-manager-sdk-v2 2>/dev/null | head -30";
echo ===UPLOAD_MASTER_FIRMWARE_API===;
docker exec frontend sh -c "grep -rohE \"/[a-zA-Z0-9_/{}.-]*(firmware|firmwares|ota)[a-zA-Z0-9_/{}.-]*\" /app/upload-master 2>/dev/null | sort | uniq -c | sort -rn | head -30";
echo ===UPLOAD_MASTER_ENDPOINT_PATTERNS===;
docker exec frontend sh -c "grep -rohE \"[\\\"x27](https?://[^\\\"x27]{4,100})[\\\"x27]\" /app/upload-master/static/js 2>/dev/null | grep -aiE \"minio|9000|30802|storage|file\" | head -20";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
