#!/usr/bin/env bash
# 重试能力侦察：司空原生前端 JS 里的上传重试 API + tas 下发过的 services 指令集 + REST 路由
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===FRONT_JS_FILES===;
docker exec frontend sh -c "find / -maxdepth 5 -name \"*.js\" -size +50k 2>/dev/null | grep -v node_modules | head -12";
echo ===FRONT_GREP_UPLOAD_RETRY===;
docker exec frontend sh -c "grep -rhoaE \"/(api|manage|http)[a-zA-Z0-9/_-]*(upload|media)[a-zA-Z0-9/_-]*\" /usr/share/nginx/html /var/www 2>/dev/null | sort -u | head -20";
echo ===FRONT_GREP_RETRY_WORDS===;
docker exec frontend sh -c "grep -rhoaE \"[a-zA-Z0-9/_-]*(retryUpload|uploadRetry|reupload|re-upload)[a-zA-Z0-9/_-]*\" /usr/share/nginx/html /var/www 2>/dev/null | sort -u | head -10";
echo ===TAS_SENT_SERVICES_24H===;
docker logs tas-service --since 24h 2>&1 | grep -a "send emqx msg" | grep -a "/services" | grep -ao "method.....[a-z_]*" | sort | uniq -c | sort -rn | head -25;
echo ===TAS_HTTP_PATHS_24H===;
docker logs tas-service --since 24h 2>&1 | grep -ao "path...//[a-z0-9/_-]*" | sort -u | head -40;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
