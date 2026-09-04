#!/usr/bin/env bash
# 媒体清理侦察（一）：机场媒体删除/优先上传能力探查
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===ALL_CONTAINERS===;
docker ps --format "{{.Names}}" | sort;
echo ===MEDIA_PRIORITY_IN_TAS===;
docker logs tas-service --since 24h 2>&1 | grep -aiE "priority|优先" | grep -av "log_priority" | head -8;
echo ===SERVICE_METHODS_SUPPORTED===;
docker logs tas-service --since 24h 2>&1 | grep -ao "\"method\":\"[a-z_]*\"" | sort -u | head -40;
echo ===PF_MEDIA_ROUTES===;
docker logs pf-media-core --since 24h 2>&1 | grep -ao "\"path\":\"[^\"]*\"" | sort -u | head -30;
echo ===GREP_DELETE_MEDIA_API===;
for C in pf-media-core es-task pf-control-core; do
  echo "--$C--";
  docker logs $C --since 24h 2>&1 | grep -aiE "media.*(delete|remove)|(delete|remove).*media" | grep -av flight_replay | head -5;
done;
echo ===FRONTEND_FILES===;
docker ps --format "{{.Names}}" | grep -aiE "front|web|nginx|paas" ;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
