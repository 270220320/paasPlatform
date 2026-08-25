#!/usr/bin/env bash
# 诊断第二阶段：扫司空核心服务日志中的 bind/dock 痕迹 + 确认部署版本
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c 'for c in tas-emqx-service tas-service pf-device-service pf-control-core pf-gateway-service es-management es-privatization-system live-service drtc-manager; do N=$(docker logs --since 48h $c 2>&1 | grep -icE "bind|绑定"); [ "$N" -gt 0 ] && echo "==== $c ($N hits) ====" && docker logs --since 48h $c 2>&1 | grep -iE "bind|绑定" | tail -10; done; echo ====VERSION====; cat /opt/cloudApi2_3/installer-data/*version* 2>/dev/null; ls /opt/cloudApi2_3/installer-data 2>/dev/null | head; find /opt/cloudApi2_3/install -maxdepth 2 -iname "*version*" 2>/dev/null | head; echo ====EMQX_LOGLEVEL====; docker logs --tail 5 emqx 2>&1'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
