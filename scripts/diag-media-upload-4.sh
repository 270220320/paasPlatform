#!/usr/bin/env bash
# 媒体诊断（四）：定位并解析本次任务 kmz 拍照动作 + pf-media-core 日志 + 相机 SD 状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===KMZ_URL_FROM_TRAEFIK===;
docker logs traefik --since 100m 2>&1 | grep -a "flighttask_resource_get" | tail -2;
URL=$(docker logs traefik --since 100m 2>&1 | grep -ao "http://192.168.76.141:30802/[^\\"]*kmz" | tail -1);
echo "URL=$URL";
KEY=$(echo "$URL" | sed "s|http://192.168.76.141:30802/||; s|?.*||");
echo "KEY=$KEY";
echo ===LOCATE_IN_MINIO===;
for B in file-storage-privatization task-scheduler; do
  P="/data/$B/$KEY";
  if docker exec minio sh -c "[ -f \"$P\" ]"; then echo "FOUND: $P"; docker exec minio cat "$P" > /tmp/task.kmz; fi;
done;
# 也可能在 file-storage 数据卷挂载点直接找（宿主机）
if [ ! -f /tmp/task.kmz ]; then
  V=$(docker inspect minio --format "{{range .Mounts}}{{.Source}}:{{.Destination}} {{end}}");
  echo "VOLUMES=$V";
  for MP in $(echo "$V" | tr ":" " " | awk "{print \$1}"); do
    [ -d "$MP" ] && ls "$MP" 2>/dev/null | head -5;
  done;
fi;
echo ===KMZ_PARSE===;
if [ -f /tmp/task.kmz ]; then
  ls -la /tmp/task.kmz;
  unzip -l /tmp/task.kmz;
  echo ---ACTIONS---;
  unzip -p /tmp/task.kmz wpmz/template.kml > /tmp/template.kml 2>/dev/null;
  grep -c "Placemark" /tmp/template.kml;
  grep -oE "<wpml:actionActuatorFunc>[^<]*</wpml:actionActuatorFunc>" /tmp/template.kml | sort | uniq -c;
  grep -oE "actionType=\"[^\"]*\"" /tmp/template.kml | sort | uniq -c;
  echo ---AUTOGRID---;
  grep -oE "<wpml:autoFlightSpeed>[^<]*|<wpml:executeHeight>[^<]*|waylineType[^ /]*" /tmp/template.kml | head -8;
else
  echo "KMZ NOT FOUND";
fi;
echo ===PF_MEDIA_CORE_LOGS===;
docker logs pf-media-core --since 100m 2>&1 | tail -25;
echo ===OSD_MEDIA_STATE===;
docker logs tas-service --since 30m 2>&1 | grep -ao "\"media_upload\":[^,}]*\|\"sd_card\":[^,}]*\|\"remain_upload\":[^,}]*\|\"upload_state\":[^,}]*" | sort | uniq -c | head -10;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
