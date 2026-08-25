#!/usr/bin/env bash
# 诊断第九阶段：Redis 缓存内容 + PG devices 表定位与状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ====REDIS_DEVICE_DRONE====;
docker exec redis redis-cli --no-raw GET "common_310_device:1581F8HGX252500A009X" | head -c 1500; echo;
echo ====REDIS_APPID_DOCK====;
docker exec redis redis-cli GET "tas:sn:appid:8UUXN2K00A0144";
echo ====REDIS_APPID_DRONE====;
docker exec redis redis-cli GET "tas:sn:appid:1581F8HGX252500A009X";
echo ====REDIS_TOPO====;
docker exec redis redis-cli GET "common_310_device:topo:8UUXN2K00A0144" | head -c 800; echo;
echo ====PG1_LIST====;
docker exec postgres_instance_1 psql -U app_rw -Atc "select datname from pg_database" 2>&1;
echo ====PG2_LIST====;
docker exec postgres_instance_2 psql -U app_rw -Atc "select datname from pg_database" 2>&1'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
