#!/usr/bin/env bash
# 机场 8UUXN2K00A0144 掉线诊断：EMQX 监听/当前连接/认证日志/OSD/Redis/绑定记录/机场网络
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
SN=8UUXN2K00A0144;
echo ====EMQX_1883_LISTEN====;
ss -tln | grep 1883;
echo ====EMQX_1883_ESTABLISHED====;
ss -tn state established "( sport = :1883 )" | head -15;
echo ====TAS_EMQX_AUTH_24H====;
docker logs --since 24h tas-emqx-service 2>&1 | grep -aE "$SN|verifyAppid|login|password error" | tail -25;
echo ====TAS_OSD_COUNT_30MIN====;
docker logs --since 30m tas-service 2>&1 | grep -ac "\"sn\":\"$SN\"";
echo ====TAS_LAST_OSD====;
docker logs --since 24h tas-service 2>&1 | grep -a "\"sn\":\"$SN\"" | tail -1 | head -c 260; echo;
echo ====REDIS_APPID====;
docker exec redis redis-cli GET "tas:sn:appid:$SN";
echo ====REDIS_TOPO====;
docker exec redis redis-cli --no-raw GET "common_310_device:topo:$SN" | head -c 400; echo;
echo ====MYSQL_BINDING====;
docker exec mysql_instance_1 mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -e "select sn,client_id,created_at,updated_at from ThingAccessService.device_app_bindings" 2>/dev/null;
echo ====PING_DOCK_76118====;
ping -c 3 -W 2 192.168.76.118 2>&1 | tail -3;
echo ====EMQX_CTL_CLIENTS====;
docker exec emqx emqx_ctl clients list 2>&1 | grep -aiE "client_id|8UUX|thing" | head -12'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
