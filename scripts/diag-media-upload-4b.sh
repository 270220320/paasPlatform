#!/usr/bin/env bash
# 媒体诊断（四b）：直接解析 kmz 拍照动作（路径已从 traefik 日志确认）+ pf-media-core + OSD
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===KMZ_DIR===;
docker exec minio sh -c "ls -la /data/file-storage-privatization/8815da3c-3cfa-45d5-b40d-5f8adf21135c/";
echo ===KMZ_FETCH===;
docker exec minio sh -c "cat /data/file-storage-privatization/8815da3c-3cfa-45d5-b40d-5f8adf21135c/*.kmz" > /tmp/task.kmz;
ls -la /tmp/task.kmz;
echo ===KMZ_LIST===;
unzip -l /tmp/task.kmz;
echo ===KMZ_ACTIONS===;
unzip -p /tmp/task.kmz wpmz/template.kml > /tmp/template.kml 2>/dev/null;
echo "Placemark_count=$(grep -c Placemark /tmp/template.kml)";
echo "--actionActuatorFunc--";
grep -oE "<wpml:actionActuatorFunc>[^<]*" /tmp/template.kml | sort | uniq -c;
echo "--actionType--";
grep -oE "actionType=\"[^\"]*\"" /tmp/template.kml | sort | uniq -c;
echo "--takePhoto/survey hints--";
grep -oiE "takePhoto|shootPhoto|smartTrack|survey|mapping|WaylineHeadingParam|takeOffSecurityHeight" /tmp/template.kml | sort | uniq -c | head;
echo ===PF_MEDIA_CORE===;
docker logs pf-media-core --since 100m 2>&1 | grep -aiE "error|fail|upload|media" | tail -20;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
