#!/usr/bin/env bash
# 诊断（四）：从司空前端 JS 挖固件上传 API 路径 + 各服务日志文件挂载点
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===FRONTEND_HTML_ROOT===;
docker exec frontend sh -c "ls /usr/share/nginx/html 2>/dev/null || ls /usr/local/openresty/nginx/html 2>/dev/null || ls /var/www 2>/dev/null || ls / | head -20" 2>&1;
echo ===GREP_FIRMWARE_API_IN_JS===;
docker exec frontend sh -c "grep -rohE \"/[a-zA-Z0-9_/-]*(firmware|ota)[a-zA-Z0-9_/{}.-]*\" /usr/share/nginx/html 2>/dev/null | sort | uniq -c | sort -rn | head -40" 2>&1;
echo ===GREP_UPLOAD_TOKEN_API===;
docker exec frontend sh -c "grep -rohE \"/[a-zA-Z0-9_/-]*(upload-token|upload_token|storage/sts|get-sts|presign|put-object|temporary)[a-zA-Z0-9_/{}.-]*\" /usr/share/nginx/html 2>/dev/null | sort -u | head -40" 2>&1;
echo ===TAS_LOGS_MOUNT===;
docker inspect tas-service --format "{{json .Mounts}}" | tr "," "\n" | grep -iE "log|Source" | head -20;
echo ===FILE_STORAGE_MOUNT===;
docker inspect file-storage --format "{{json .Mounts}}" | tr "," "\n" | grep -iE "log|Source" | head -20;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
