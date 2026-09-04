#!/usr/bin/env bash
# 云服务切换失败排查（九）：切换时刻机场推送的全部 events（找失败明细）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===DOCK_EVENTS_0345_0350===;
docker logs tas-service --since 40m 2>&1 | grep -a "2026-08-25T03:4[5-9]" | grep -a "thing/product/8UUXN2K00A0144/events" | grep -av file_upload_callback | cut -c1-800 | head -12;
echo ===SWITCH_EVENT_ANY===;
docker logs tas-service --since 40m 2>&1 | grep -a "cloud_environment" | grep -av "sent_service\|services\"" | cut -c1-800 | head -8;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
