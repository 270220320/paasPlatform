#!/usr/bin/env bash
# 机场掉线二诊：容器重启时间/EMQX重连循环检测/订阅列表/业务消息通道
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ====CONTAINER_STARTED_AT====;
for c in emqx tas-emqx-service tas-service pf-device-service; do echo -n "$c: "; docker inspect $c --format "{{.State.StartedAt}} restarts={{.RestartCount}}"; done;
echo ====EMQX_SNAPSHOT1====;
docker exec emqx emqx_ctl clients list 2>/dev/null | grep -a 8UUXN2K00A0144;
sleep 45;
echo ====EMQX_SNAPSHOT2_AFTER_45S====;
docker exec emqx emqx_ctl clients list 2>/dev/null | grep -a 8UUXN2K00A0144;
echo ====DOCK_SUBSCRIPTIONS====;
docker exec emqx emqx_ctl subscriptions list 2>/dev/null | grep -a 8UUXN2K00A0144;
echo ====TAS_ANY_EMQX_MSG_10MIN====;
docker logs --since 10m tas-service 2>&1 | grep -ac "receive emqx msg";
echo ====TAS_RECENT_NON_OSD====;
docker logs --since 30m tas-service 2>&1 | grep -avE "device_osd|RabbitmqPublish|IsBestGateway|properties/list|receive emqx msg" | grep -aiE "error|warn|8UUXN2K00A0144" | tail -12'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
