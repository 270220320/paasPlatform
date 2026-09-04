#!/usr/bin/env bash
# 云服务切换失败排查（三）：tas-service 里 325001 的具体原因（按 bid/identifier 追）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TAS_325001_LINES===;
docker logs tas-service --since 40m 2>&1 | grep -a "325001" | tail -6 | cut -c1-900;
echo ===TAS_SWITCH_LINES===;
docker logs tas-service --since 40m 2>&1 | grep -a "cloud_environment_switch" | tail -10 | cut -c1-900;
echo ===TAS_BID_TRACE===;
docker logs tas-service --since 40m 2>&1 | grep -a "6458d2a6-01f2-454c-b1e8-a94538608cd2\|75511904-2654-4b09-b7d4-1328294b1ce4" | tail -8 | cut -c1-900;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
