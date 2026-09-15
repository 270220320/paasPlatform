#!/usr/bin/env bash
# 司空3.1升级（四）：以root启动3.1可视化安装器
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===START_INSTALLER===';
cd /opt/cloudApi2_3.1 && echo 'Tksc@197653' | sudo -S ./run_installer.sh 2>&1 | tail -8;
echo '===PING_CHECK===';
sleep 2;
curl -fs http://127.0.0.1:40000/ping && echo " <- ping ok";
echo '===LISTEN_ADDR===';
echo 'Tksc@197653' | sudo -S ss -tlnp 2>/dev/null | grep 40000;
echo '===INSTALLER_PROC===';
pgrep -af es-privatization-installer;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
