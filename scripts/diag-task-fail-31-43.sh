#!/usr/bin/env bash
# 219015 排查（二十八）：验证 3.1 前端入口实际内容（决定绑定途径）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===FRONT_HTML_LS_FULL===';
SE docker exec frontend sh -c 'ls -la /usr/share/nginx/html/';
echo '===INDEX_CONTENT===';
SE docker exec frontend sh -c 'head -c 500 /usr/share/nginx/html/index.html';
echo; echo '===CURL_443===';
curl -sk -m 6 -o /dev/null -w 'https443 code=%{http_code} size=%{size_download}\n' https://127.0.0.1/ ;
curl -sk -m 6 https://127.0.0.1/ | head -c 300; echo;
echo '===CURL_30801===';
curl -sk -m 6 -o /dev/null -w '30801 code=%{http_code} size=%{size_download}\n' https://127.0.0.1:30801/ ;
curl -sk -m 6 https://127.0.0.1:30801/ | head -c 300; echo;
echo '===CURL_30820===';
curl -sk -m 6 -o /dev/null -w '30820 code=%{http_code} size=%{size_download}\n' https://127.0.0.1:30820/ ;
echo '===CURL_80===';
curl -s -m 6 -o /dev/null -w 'http80 code=%{http_code} size=%{size_download}\n' http://127.0.0.1/ ;
echo '===MINIO_STATIC_LS===';
SE docker exec minio-static sh -c 'ls / 2>/dev/null | head; ls /data 2>/dev/null | head' | head -12;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
