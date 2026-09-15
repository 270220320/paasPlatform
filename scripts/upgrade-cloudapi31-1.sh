#!/usr/bin/env bash
# 司空3.1升级（一）：侦察安装脚本 + 当前容器 + 磁盘空间
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===RUN_INSTALLER_SH===';
cat /opt/cloudApi2_3.1/run_installer.sh;
echo '===DOCKER_PS===';
echo 'Tksc@197653' | sudo -S docker ps --format "table {{.Names}}\t{{.Status}}" 2>/dev/null;
echo '===DISK===';
df -h /;
echo '===OLD_INSTALL_DIR===';
ls /opt/cloudApi2_3/install/ 2>/dev/null | head -20;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
