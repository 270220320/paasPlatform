#!/usr/bin/env bash
# 司空3.1升级（七）：查 fhaio-proxy/k3s 管理方式 + k3s 内负载
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===SYSTEMD_UNITS===';
echo 'Tksc@197653' | sudo -S systemctl list-units --type=service --all 2>/dev/null | grep -Ei 'k3s|fhaio|fh2|cloudapi|es-' ;
echo '===SYSTEMD_UNIT_FILES===';
echo 'Tksc@197653' | sudo -S systemctl list-unit-files --type=service 2>/dev/null | grep -Ei 'k3s|fhaio|fh2|cloudapi|es-';
echo '===K3S_PODS===';
echo 'Tksc@197653' | sudo -S k3s kubectl get pods -A 2>&1 | head -30;
echo '===K3S_NS===';
echo 'Tksc@197653' | sudo -S k3s kubectl get ns 2>&1;
echo '===FHAIO_UNIT_DETAIL===';
echo 'Tksc@197653' | sudo -S systemctl cat fhaio-proxy 2>&1 | head -25;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
