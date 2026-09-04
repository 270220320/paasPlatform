#!/usr/bin/env bash
# 云服务切换失败排查（十三）：EMQX 原始输出/API 查机场连接 + tas 日志时间格式校准
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===EMQX_CTL_RAW===;
docker exec emqx emqx ctl clients list 2>&1 | head -8;
echo ===EMQX_API_DOCK3===;
curl -s -u "admin:public" "http://127.0.0.1:18083/api/v5/clients?clientid=8UUXN2K00A0144" | head -c 600; echo;
echo ===EMQX_API_DOCK3_LIKE===;
curl -s -u "admin:public" "http://127.0.0.1:18083/api/v5/clients?clientid=like(*8UUXN2K*)" | head -c 800; echo;
echo ===EMQX_API_ALL_COUNT===;
curl -s -u "admin:public" "http://127.0.0.1:18083/api/v5/clients?page=1&limit=5" | head -c 800; echo;
echo ===TAS_LOG_SAMPLE===;
docker logs tas-service --tail 5 2>&1 | cut -c1-300;
echo ===TAS_RECENT_144_ANY===;
docker logs tas-service --since 50m 2>&1 | grep -a "8UUXN2K00A0144" | tail -8 | cut -c1-600;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
