#!/usr/bin/env bash
# 重试能力侦察（二）：/app 全部 JS 里的媒体上传/重试 API 彻底扫描
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===APP_DIRS===;
docker exec frontend sh -c "ls /app | head -20";
echo ===GREP_MEDIA_API_IN_ALL_JS===;
docker exec frontend sh -c "grep -rhoaE \"/(api|manage)[a-zA-Z0-9/_.-]*media[a-zA-Z0-9/_.-]*\" /app 2>/dev/null | sort -u | head -25";
echo ===GREP_RETRY_ANY===;
docker exec frontend sh -c "grep -rhoaE \"[a-zA-Z0-9/_.-]*(retry|Retry)[a-zA-Z0-9/_.-]*\" /app/*/static/js 2>/dev/null | grep -aiE \"upload|media\" | sort -u | head -10";
echo ===GREP_UPLOAD_KEYWORD===;
docker exec frontend sh -c "grep -rhoaE \"[\\\"/](upload[a-zA-Z0-9/_-]{3,40})\" /app/*/static/js/*.js 2>/dev/null | sort | uniq -c | sort -rn | head -20";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
