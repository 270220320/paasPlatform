#!/usr/bin/env bash
# 云服务切换失败排查（十四）：--tail 大窗口绕过 --since 坑，直查 03:26 切换请求的下发与机场回复
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TAS_CLOUD_ENV_ANY===;
docker logs tas-service --tail 200000 2>&1 | grep -a "cloud_environment" | tail -15 | cut -c1-1000;
echo ===TAS_0326_MINUTE===;
docker logs tas-service --tail 200000 2>&1 | grep -a "2026-09-04T03:26" | grep -avE "osd|/events\".*file_upload" | tail -20 | cut -c1-1000;
echo ===PFDEVICE_SWITCH_TAIL===;
docker logs pf-device-service --tail 200000 2>&1 | grep -a "cloud-environment\|cloud_environment" | tail -10 | cut -c1-900;
echo ===PFDEVICE_0326===;
docker logs pf-device-service --tail 200000 2>&1 | grep -a "2026-09-04T03:26" | tail -15 | cut -c1-700;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
