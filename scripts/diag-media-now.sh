#!/usr/bin/env bash
# 媒体现状复查：remain_upload 是否仍为 4 / 媒体是否已到库 / 上传流量是否恢复
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===REMAIN_UPLOAD_SERIES===;
docker logs tas-service --since 120m 2>&1 | grep -a remain_upload | grep -ao "@timestamp\":\"2026-08-25T[0-9:]*\|remain_upload\":[0-9]*" | paste - - | awk "{print \$1, \$2}" | sed "s/@timestamp....//; s/remain_upload.../remain_upload=/" | uniq -f1 | tail -8;
echo ===MEDIA_CALLBACKS_NON_REPLAY===;
docker logs tas-service --since 120m 2>&1 | grep -a file_upload_callback | grep -a "receive emqx" | grep -av flight_replay | grep -ao "@timestamp\":\"[0-9T:-]*\|\"name\":\"[^\"]*\|\"flight_id\":\"[0-9a-f-]*" | paste - - - | tail -8;
echo ===MINIO_82d6_NOW===;
docker exec minio sh -c "ls -la /data/file-storage-privatization/82d606ec10d02a884c51e2719dfe2bb3/";
echo ===DOCK_STORAGE_NOW===;
docker logs tas-service --since 10m 2>&1 | grep -a "thing/product/8UUXN2K00A0144/osd" | grep -ao "storage[^}]*}" | sort | uniq -c | head -3;
echo ===TRAEFIK_PUT_120M===;
docker logs traefik --since 120m 2>&1 | grep -aE "PUT" | grep -ac file-storage;
echo ===STORAGE_CONFIG_HB_10M===;
docker logs traefik --since 10m 2>&1 | grep -ac storage_config_get;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
