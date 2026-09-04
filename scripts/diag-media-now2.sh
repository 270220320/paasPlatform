#!/usr/bin/env bash
# 媒体现状复查（二）：remain_upload 当前值 + 03:37 回调全文 + traefik PUT + MinIO 新文件 + 机场是否重启
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===REMAIN_UPLOAD_LAST_VALUES===;
docker logs tas-service --since 15m 2>&1 | grep -a remain_upload | grep -ao "remain_upload[^,}]*" | sort | uniq -c;
echo ===CALLBACKS_20M_FULL===;
docker logs tas-service --since 20m 2>&1 | grep -a file_upload_callback | grep -a "receive emqx" | cut -c1-450 | tail -6;
echo ===TRAEFIK_PUT_20M_RAW===;
docker logs traefik --since 20m 2>&1 | grep -a "PUT " | grep -av "metrics\|health" | tail -8;
echo ===MINIO_NEW_SINCE_0330===;
docker exec minio sh -c "find /data/file-storage-privatization -newermt \"2026-08-25 03:30\" -maxdepth 2 2>/dev/null" | head -15;
echo ===DOCK_STORAGE_SEQUENCE_15M===;
docker logs tas-service --since 15m 2>&1 | grep -a "thing/product/8UUXN2K00A0144/osd" | grep -ao "@timestamp\":\"2026-08-25T[0-9:]*\|used\":[0-9]*" | paste - - | sed "s/@timestamp....//; s/used.../used=/" | uniq -f1 | tail -12;
echo ===DOCK_REBOOT_TRACE===;
docker logs tas-service --since 40m 2>&1 | grep -aiE "offline|update_topo|topo_add|8UUXN2K00A0144.*login" | tail -6;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
