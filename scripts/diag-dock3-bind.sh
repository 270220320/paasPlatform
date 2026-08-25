#!/usr/bin/env bash
# 诊断 Dock 3 绑定问题：SSH 到 192.168.76.141 收集 EMQX 日志/配置/部署版本
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c 'V=/var/lib/docker/volumes/e5b48cee9adb6eca1a8ea8a05e02529a2b82ed14233acbee658112b21b7472fa/_data; echo ===VOL===; ls -la $V 2>/dev/null; echo ===LOGTAIL===; for f in $V/*.log $V/emqx.log.1; do [ -f $f ] && echo ==FILE:$f== && tail -n 40 $f; done; echo ===DEPLOYDIR===; ls /opt/cloudApi2_3 2>/dev/null | head -25; echo ===EMQX_ETC===; ls /opt/cloudApi2_3/install/conf/middleware-service/emqx-broker/etc 2>/dev/null; echo ===EMQX_CONF===; grep -iE "authn|anonymous|listener|password" /opt/cloudApi2_3/install/conf/middleware-service/emqx-broker/etc/emqx.conf 2>/dev/null | head -40'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
