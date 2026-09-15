#!/usr/bin/env bash
# 司空3.1升级（十）：清理k3s残留 + 备份旧k3s数据，为3.1全新安装让路
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===K3S_KILLALL===';
if [ -f /usr/local/bin/k3s-killall.sh ]; then
  echo 'Tksc@197653' | sudo -S /usr/local/bin/k3s-killall.sh 2>&1 | tail -5;
else
  echo "killall script not found";
fi;
sleep 3;
echo '===PKILL_RESIDUAL===';
echo 'Tksc@197653' | sudo -S pkill -9 -f 'containerd-shim-runc-v2.*run/k3s' 2>/dev/null; echo "shim_kill_rc=$?";
echo 'Tksc@197653' | sudo -S pkill -9 -f 'promtail.*promtail_k3s' 2>/dev/null; echo "promtail_kill_rc=$?";
sleep 2;
echo '===BACKUP_OLD_K3S_DATA===';
echo 'Tksc@197653' | sudo -S bash -c 'TS=20260908;
  [ -d /var/lib/rancher/k3s ] && mv /var/lib/rancher/k3s /var/lib/rancher/k3s.bak-$TS && echo "var_lib backed up";
  [ -d /etc/rancher/k3s ] && mv /etc/rancher/k3s /etc/rancher/k3s.bak-$TS && echo "etc_rancher backed up";
  rm -rf /run/k3s /run/flannel 2>/dev/null; echo "run dirs cleaned";
  ls -ld /var/lib/rancher/k3s* /etc/rancher/k3s* 2>/dev/null';
echo '===VERIFY_CLEAN===';
pgrep -af 'k3s|containerd-shim' || echo "no_k3s_related_procs";
ls /var/lib/rancher/ 2>/dev/null;
echo '===INSTALL_LOG_DETAIL===';
LATEST_INSTALL=$(ls -t /opt/cloudApi2_3.1/installer-data/logs/install/*.log 2>/dev/null | head -1);
echo "--- $LATEST_INSTALL ---";
echo 'Tksc@197653' | sudo -S grep -aiE 'k3s|fatal|fail' "$LATEST_INSTALL" 2>/dev/null | tail -20;
echo '===INSTALLER_OK===';
curl -fs http://127.0.0.1:40000/ping && echo " <- installer ok";
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
