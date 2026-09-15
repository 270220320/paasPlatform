#!/usr/bin/env bash
# 司空3.1升级（八）：停禁 3.0 残留系统服务 fhaio-proxy + k3s，验证端口释放
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===STOP_DISABLE_FHAIO===';
echo 'Tksc@197653' | sudo -S systemctl stop fhaio-proxy 2>&1;
echo 'Tksc@197653' | sudo -S systemctl disable fhaio-proxy 2>&1;
echo '===STOP_DISABLE_K3S===';
echo 'Tksc@197653' | sudo -S systemctl stop k3s 2>&1;
echo 'Tksc@197653' | sudo -S systemctl disable k3s 2>&1;
sleep 5;
echo '===SERVICE_STATUS===';
echo 'Tksc@197653' | sudo -S systemctl is-active fhaio-proxy k3s 2>&1;
echo '===PORT_RELEASE_CHECK===';
echo 'Tksc@197653' | sudo -S ss -tlnp 2>/dev/null | grep -E ':(30887|30826|30827)\b' || echo "ALL_THREE_PORTS_FREE";
echo '===PROC_CHECK===';
pgrep -af 'fhaio-proxy|k3s server' || echo "no_residual_procs";
echo '===INSTALLER_STILL_OK===';
curl -fs http://127.0.0.1:40000/ping && echo " <- installer ok";
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
