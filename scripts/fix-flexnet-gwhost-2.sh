#!/usr/bin/env bash
# 修复（二）：dynamic_conf.yaml 持久化 multipleIpSnMap（防重启/Redis清空复发）+ 验证 Redis 命中日志
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
CONF=/opt/cloudApi2_3/install/conf/middleware-service/traefik/dynamic_conf.yaml;
echo ===BEFORE===;
grep -n "multipleIpSnMap" $CONF;
cp $CONF ${CONF}.bak.$(date +%s);
sed -i "s/multipleIpSnMap: \"[^\"]*\"/multipleIpSnMap: \"8UUXN2K00A0144:192.168.76.141\"/" $CONF;
echo ===AFTER===;
grep -n "multipleIpSnMap" $CONF;
echo ===WAIT_75S_FOR_CALLBACK===;
sleep 75;
echo ===RECENT_FLEXNET_LOGS===;
docker logs traefik --since 3m 2>&1 | grep -aE "get deviceHost from redis|redis miss|storage_config_get|resp diff" | tail -12;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
