#!/usr/bin/env bash
# 媒体清理侦察（二）：本次架次(82d606ec)媒体是否已到库 + 非replay回调 + 媒体清理API探查
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TASK_FLIGHT_DIR_82d6===;
docker exec minio sh -c "ls -laR /data/file-storage-privatization/82d606ec10d02a884c51e2719dfe2bb3/ 2>/dev/null | head -25";
echo ===NON_REPLAY_CALLBACKS_30M===;
docker logs tas-service --since 30m 2>&1 | grep -a "file_upload_callback" | grep -a "receive emqx" | grep -av "flight_replay" | grep -ao "\"name\":\"[^\"]*\"\|\"flight_id\":\"[^\"]*\"\|\"module\":[0-9]*\|\"progress\":[0-9]*" | paste - - - - 2>/dev/null | tail -12;
echo ===82d6_CALLBACKS===;
docker logs tas-service --since 40m 2>&1 | grep -a "82d606ec" | grep -a "file_upload_callback" | tail -6;
echo ===PRIORITY_AFTER_TASK===;
docker logs tas-service --since 40m 2>&1 | grep -a "highest_priority" | tail -4;
echo ===MEDIA_DELETE_API_SEARCH===;
for C in pf-device-service pf-atc-service es-management pf-gateway-service; do
  R1=$(docker logs $C --since 24h 2>&1 | grep -aiE "airport.*media|media.*(delete|clear|clean)|delete.*file" | grep -av flight_replay | head -3);
  [ -n "$R1" ] && { echo "--$C--"; echo "$R1"; };
done;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
