#!/usr/bin/env bash
# 诊断第八阶段：Redis 设备状态缓存 + PG 连通测试
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ====PG_TEST====;
docker exec postgres_instance_1 psql -U app_rw -Atc "select 1" 2>&1 | head -2;
echo ====PG_PASSWD====;
docker inspect postgres_instance_1 --format "{{json .Config.Env}}" | tr "," "\n" | grep -oE "POSTGRES_PASSWORD=[^ ]+" ;
echo ====REDIS_KEYS====;
docker exec redis redis-cli --scan --pattern "*8UUXN2K00A0144*" 2>/dev/null | head -10;
docker exec redis redis-cli --scan --pattern "*1581F8HGX252500A009X*" 2>/dev/null | head -5'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
