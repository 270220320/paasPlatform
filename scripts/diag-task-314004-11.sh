#!/usr/bin/env bash
# 诊断（十一）：读 dynamic_conf.yaml 的 flexNet 配置 + 插件源码改写逻辑
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===DYNAMIC_CONF===;
cat /opt/cloudApi2_3/install/conf/middleware-service/traefik/dynamic_conf.yaml;
echo ===PLUGIN_FILES===;
ls -la /opt/cloudApi2_3/install/traefik/plugin/flexnet/ | head -20;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
