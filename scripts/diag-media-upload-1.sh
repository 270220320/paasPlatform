#!/usr/bin/env bash
# 媒体诊断（一）：最新任务执行情况 + 媒体上传全链路日志（es-task/tas/file-storage/traefik/minio）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===ES_TASK_MEDIA_LOGS===;
docker logs es-task --since 90m 2>&1 | grep -aiE "media|upload|file" | tail -40;
echo ===TAS_MEDIA_LOGS===;
docker logs tas-service --since 90m 2>&1 | grep -aiE "media|upload" | tail -30;
echo ===FILE_STORAGE_LOGS===;
docker logs file-storage --since 90m 2>&1 | grep -avE "\x00" | tail -40;
echo ===MINIO_PUT_LOGS===;
docker logs minio --since 90m 2>&1 | grep -aE "PUT|GET" | tail -30;
echo ===TRAEFIK_FLIGHTTASK_KMZ===;
docker logs traefik --since 90m 2>&1 | grep -a "flighttask_resource_get" | tail -3;
echo ===TRAEFIK_MEDIA===;
docker logs traefik --since 90m 2>&1 | grep -aiE "media|storage" | tail -15;
echo ===EMQX_MEDIA_EVENTS===;
docker logs tas-service --since 90m 2>&1 | grep -aiE "events|flighttask_progress" | tail -15;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
