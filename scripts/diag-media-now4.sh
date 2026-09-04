#!/usr/bin/env bash
# 媒体现状复查（四）：traefik 03:36-03:38 PUT 路径 + 回调全文（媒体落在哪个 object key）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TRAEFIK_PUT_0336_0339===;
docker logs traefik --since 3h --until 0m 2>&1 | grep -a "2026/08/25 03:3[6-9]" | grep -a "PUT" | grep -ao "PUT [^ ]*" | sort | uniq -c;
echo ===CALLBACK_LINE_SAMPLE===;
docker logs tas-service --since 40m 2>&1 | grep -a file_upload_callback | grep -a "03:37:3" | head -2 | cut -c1-600;
echo ===MEDIA_DB_TABLES===;
docker exec postgres_instance_1 psql -U app_rw -d es_media -c "\dt" 2>/dev/null | head -20;
docker exec postgres_instance_1 psql -U app_rw -l 2>/dev/null | grep -aiE "media|task" ;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
