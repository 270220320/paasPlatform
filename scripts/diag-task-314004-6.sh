#!/usr/bin/env bash
# 诊断（六）：司空前端 JS 中 current_step 步骤文案映射 + 314004 相关文案
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===FRONTEND_FILES===;
docker exec frontend sh -c "ls /usr/share/nginx/html 2>/dev/null || ls /usr/local/openresty/nginx/html 2>/dev/null || find / -maxdepth 4 -name index.html -path *html* 2>/dev/null | head -3";
echo ===GREP_STEP_MAP===;
docker exec frontend sh -c "grep -rhoaE \"(currentStep|current_step|stepName|step:)[^,;{}]{0,60}\" /usr/share/nginx/html/static/js/ 2>/dev/null | sort -u | head -40";
echo ===GREP_314004===;
docker exec frontend sh -c "grep -rhoaE \".{0,80}314004.{0,80}\" /usr/share/nginx/html/ 2>/dev/null | head -10";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
