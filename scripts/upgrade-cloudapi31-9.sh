#!/usr/bin/env bash
# 司空3.1升级（九）：k3s 安装失败取证
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===K3S_PROC===';
pgrep -af 'k3s' || echo "no_k3s_proc";
echo '===K3S_SERVICE===';
echo 'Tksc@197653' | sudo -S systemctl status k3s --no-pager -l 2>&1 | head -15;
echo '===K3S_JOURNAL_TAIL===';
echo 'Tksc@197653' | sudo -S journalctl -u k3s --since '30 min ago' --no-pager 2>&1 | tail -25;
echo '===ETC_RANCHER===';
echo 'Tksc@197653' | sudo -S ls -la /etc/rancher/k3s/ 2>&1;
echo 'Tksc@197653' | sudo -S cat /etc/rancher/k3s/config.yaml 2>&1 | head -30;
echo '===VAR_LIB_K3S===';
echo 'Tksc@197653' | sudo -S ls /var/lib/rancher/k3s/ 2>&1;
echo '===INSTALLER_LOGS===';
ls -lt /opt/cloudApi2_3.1/installer-data/logs/ 2>/dev/null | head -8;
LATEST=$(ls -t /opt/cloudApi2_3.1/installer-data/logs/*.log 2>/dev/null | head -1);
echo "--- latest log: $LATEST ---";
echo 'Tksc@197653' | sudo -S tail -40 "$LATEST" 2>/dev/null;
echo '===K3S_INSTALL_SCRIPT===';
find /opt/cloudApi2_3.1/installer-data -name '*k3s*' 2>/dev/null | head -10;
echo '===DISK===';
df -h / | tail -1;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
