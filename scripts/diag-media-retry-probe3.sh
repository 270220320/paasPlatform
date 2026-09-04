#!/usr/bin/env bash
# 重试能力侦察（三）：定位 fh2_media_upload_retry 所在 JS + 提取上下文 API
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===LOCATE_JS_FILE===;
docker exec frontend sh -c "grep -rla fh2_media_import_newpage_upload_retry_click /app --include=*.js 2>/dev/null | head -3";
F=$(docker exec frontend sh -c "grep -rla fh2_media_import_newpage_upload_retry_click /app --include=*.js 2>/dev/null | head -1");
echo "FILE=$F";
echo ===CONTEXT_AROUND_RETRY===;
if [ -n "$F" ]; then
  docker exec frontend sh -c "grep -oaE \".{180}fh2_media_import_newpage_upload_retry_click.{180}\" $F" | head -2;
fi;
echo ===MEDIA_API_URLS_IN_FILE===;
if [ -n "$F" ]; then
  docker exec frontend sh -c "grep -oaE \"(url|URL)[\":= ]+[\x27\"][^\"\x27]{5,90}media[^\"\x27]{0,40}[\x27\"]\" $F | sort -u | head -10";
  docker exec frontend sh -c "grep -oaE \"[\x27\"]/[a-z0-9/_-]{4,60}(media|upload)[a-z0-9/_-]{0,40}[\x27\"]\" $F | sort -u | head -15";
fi;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
