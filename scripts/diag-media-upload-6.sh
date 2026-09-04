#!/usr/bin/env bash
# 媒体诊断（六）：本次架次(6dfc5552)完整事件流 + OSD 媒体状态 + 上传暂停/优先级
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo ===EVENTS_6dfc5552===;
echo Tksc@197653 | sudo -S docker logs tas-service --since 130m 2>&1 | grep -a 6dfc5552 | grep -av "receive emqx msg" | head -20;
echo ===OSD_MEDIA_FIELDS===;
echo Tksc@197653 | sudo -S docker logs tas-service --since 20m 2>&1 | grep -a "osd" | grep -ao '\\"media_upload[^,}]*\|\\"upload[^,}]*\|\\"storage[^,}]*\|\\"sd_[^,}]*' | sort | uniq -c | head -15;
echo ===UPLOAD_PAUSE_SERVICE===;
echo Tksc@197653 | sudo -S docker logs tas-service --since 130m 2>&1 | grep -aiE "upload_pause|media_pause|pause.*upload|upload.*priority|media_upload" | grep -av flight_replay | head -10;
echo ===PF_MEDIA_MEDIA_FILES===;
echo Tksc@197653 | sudo -S docker logs pf-media-core --since 130m 2>&1 | grep -avE "health|/sts" | tail -15;
echo ===ES_TASK_6dfc5552===;
echo Tksc@197653 | sudo -S docker logs es-task --since 130m 2>&1 | grep -a 6dfc5552 | head -10;
echo ===DONE===
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
