#!/usr/bin/env bash
# 修复验证（五）：前端 JS 找绑定功能入口 + pf-device 后端 bind 路由
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
echo '===INDEX_JS_PATHS===';
curl -sk -m 8 https://127.0.0.1/ -o /tmp/idx.html && grep -oE '(src|href)="[^"]+\.(js|css)"' /tmp/idx.html | head -10;
echo '===FETCH_MAIN_JS===';
JS=$(grep -oE 'src="[^"]+\.js"' /tmp/idx.html | head -1 | sed 's/src="//;s/"$//');
echo "mainjs=$JS";
if [ -n "$JS" ]; then
  case "$JS" in /*) URL="https://127.0.0.1$JS";; *) URL="https://127.0.0.1/$JS";; esac;
  curl -sk -m 30 "$URL" -o /tmp/main.js && ls -la /tmp/main.js;
fi;
echo '===JS_BIND_TEXT===';
if [ -f /tmp/main.js ]; then
  grep -aoE '[^"]{0,20}绑定[^"]{0,30}' /tmp/main.js | sort -u | head -30;
fi;
echo '===JS_BIND_API===';
if [ -f /tmp/main.js ]; then
  grep -aoE '"/[a-z0-9/_-]*bind[a-z0-9/_-]*"' /tmp/main.js | sort -u | head -20;
fi;
echo '===PFDEV_MANAGER_ROUTES===';
S docker exec pf-device-service sh -c "tr -c '[:print:]' '\n' < /app/server" | grep -aoE '(manager|api/v1)/[a-z0-9_/:{}.~-]{3,60}' | grep -aiE 'bind|airport' | sort -u | head -20;
echo '===ESMGMT_APIV1_BIND===';
S docker exec es-management sh -c "tr -c '[:print:]' '\n' < /app/main" | grep -aoE 'v1/[a-z0-9_/:{}.{}-]{3,60}' | grep -aiE 'bind|device' | grep -av 'controller\|model\|repo\|service\|pkg/' | sort -u | head -25;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
