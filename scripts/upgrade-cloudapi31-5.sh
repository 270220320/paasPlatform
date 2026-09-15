#!/usr/bin/env bash
# 司空3.1升级（五）：杀旧安装器 + 修权限 + 启动3.1新安装器
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===KILL_OLD_INSTALLER===';
echo 'Tksc@197653' | sudo -S kill 1272198 2>/dev/null; echo "kill_rc=$?";
sleep 3;
pgrep -af es-privatization-installer || echo "old_inster_gone";
echo '===CHMOD_NEW===';
echo 'Tksc@197653' | sudo -S chmod +x /opt/cloudApi2_3.1/run_installer.sh;
echo '===START_NEW_INSTALLER===';
cd /opt/cloudApi2_3.1 && echo 'Tksc@197653' | sudo -S ./run_installer.sh 2>&1 | tail -6;
echo '===VERIFY===';
sleep 2;
curl -fs http://127.0.0.1:40000/ping && echo " <- ping ok";
pgrep -af es-privatization-installer;
echo 'Tksc@197653' | sudo -S ss -tlnp 2>/dev/null | grep 40000;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
