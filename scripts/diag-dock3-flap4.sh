#!/usr/bin/env bash
# 终验：tas-service 原始日志尾部（此刻该 SN 在发什么）+ 日志轮转配置 + OSD 计数
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
SN=8UUXN2K00A0144;
echo ====LOG_CONFIG====;
docker inspect tas-service --format {{.HostConfig.LogConfig.Config}} {{.HostConfig.LogConfig.Type}};
echo ====TAIL500_SN_LINES====;
docker logs --tail 500 tas-service 2>&1 | grep -a $SN | tail -12;
echo ====TAIL3000_OSD_COUNT====;
docker logs --tail 3000 tas-service 2>&1 | grep -ac device_osd;
echo ====TAIL3000_HMS_COUNT====;
docker logs --tail 3000 tas-service 2>&1 | grep -ac device_hms;
echo ====EMQX_TOPIC_OSD_CHECK====;
docker exec emqx emqx_ctl metrics | grep -aE messages.publish|messages.receive | head -4;
echo ====DONE===='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
