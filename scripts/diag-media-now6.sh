#!/usr/bin/env bash
# 媒体现状复查（六）：tasks.media_upload_status 终态 + traefik PUT 路径（修正时间格式）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TASK_MEDIA_STATUS===;
docker exec postgres_instance_1 psql -U app_rw -d es_task -t -c "SELECT uuid, name, status, media_upload_status, updated_at FROM tasks ORDER BY updated_at DESC LIMIT 3;";
echo ===TRAEFIK_PUT_FIXED===;
docker logs traefik --since 40m 2>&1 | grep -a " - 03:3[6-9]" | grep -a "PUT" | grep -ao "PUT /[^ ?]*" | sort | uniq -c;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
