#!/usr/bin/env bash
# 媒体诊断（三）：MinIO 最近新增文件 + 本次任务目录 + kmz 拍照动作解析 + media 相关容器
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===MINIO_RECENT_FILES_3H===;
docker exec minio sh -c "find /data -type f -mmin -180 2>/dev/null" | grep -avE "loki|\.minio.sys" | head -50;
echo ===MINIO_BUCKET_BY_TIME===;
docker exec minio sh -c "ls -lat /data/file-storage-privatization 2>/dev/null | head -20";
echo ===KMZ_FIND===;
docker exec minio sh -c "find /data -name \"*.kmz\" 2>/dev/null" | tail -10;
echo ===KMZ_INSPECT_LATEST===;
K=$(docker exec minio sh -c "find /data -name \"*.kmz\" -mmin -240 2>/dev/null" | tail -1);
echo "KMZ=$K";
if [ -n "$K" ]; then
  docker cp minio:"$K" /tmp/latest.kmz >/dev/null 2>&1 || docker exec minio cat "$K" > /tmp/latest.kmz;
  unzip -l /tmp/latest.kmz | head -15;
  echo ---KML_ACTIONS---;
  unzip -p /tmp/latest.kmz wpmz/template.kml 2>/dev/null | grep -oE "actionType=[^ /]*" | sort | uniq -c;
  unzip -p /tmp/latest.kmz wpmz/template.kml 2>/dev/null | grep -oE "<wpml:actionActuatorFunc>[^<]*" | sort | uniq -c;
fi;
echo ===MEDIA_CONTAINERS===;
docker ps --format "{{.Names}}" | grep -aiE "media|file|storage";
echo ===ES_TASK_MEDIA_UPLOAD===;
docker logs es-task --since 100m 2>&1 | grep -aiE "media_upload|mediaUpload|upload_status" | tail -10;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
