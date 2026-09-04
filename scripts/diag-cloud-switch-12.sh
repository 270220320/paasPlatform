#!/usr/bin/env bash
# 云服务切换失败排查（十二）：03:26 切换请求时刻的 tas 窗口日志 + 机场 EMQX 真实在线状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TAS_0324_0332_WINDOW===;
docker logs tas-service --since 40m 2>&1 | grep -a "2026-09-04T03:2[4-9]" | grep -aiE "cloud_environment|services|8UUXN2K00A0144" | cut -c1-900 | head -25;
echo ===EMQX_DOCK3_CLIENT===;
docker exec emqx emqx ctl clients list 2>/dev/null | grep -a "8UUXN2K00A0144" | head -5;
echo ===EMQX_CLIENT_COUNT===;
docker exec emqx emqx ctl clients list 2>/dev/null | grep -ac clientid;
echo ===EMQX_1581_CLIENT===;
docker exec emqx emqx ctl clients list 2>/dev/null | grep -a "1581F8HGX252500A009X" | head -3;
echo ===TAS_ERR_WINDOW===;
docker logs tas-service --since 40m 2>&1 | grep -a "2026-09-04T03:2[4-9]" | grep -aiE "error|fail|timeout|offline" | cut -c1-700 | head -15;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
