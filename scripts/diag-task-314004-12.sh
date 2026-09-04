#!/usr/bin/env bash
# 诊断（十二）：读 flexnet 插件源码（DTCP 改写逻辑）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
P=/opt/cloudApi2_3/install/traefik/plugin/flexnet;
echo ====handler_dtcp.go====; cat $P/handler_dtcp.go;
echo ====flexnet.go====; cat $P/flexnet.go;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
