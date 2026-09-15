#!/usr/bin/env bash
# 修复验证（四）：找用户浏览器的 API 踪迹（Chrome/Edge UA）+ 机场 bind 轮询状态复查
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===BROWSER_UA_ESMGMT===';
SE docker logs es-management --since 26h 2>&1 | grep -a 'Mozilla\|Chrome/\|Edge/' | tail -8 | cut -c1-450;
echo '===BROWSER_UA_PFGW===';
SE docker logs pf-gateway-service --since 26h 2>&1 | grep -a 'Mozilla\|Chrome/\|Edge/' | tail -10 | cut -c1-450;
echo '===BROWSER_UA_PFDEV===';
SE docker logs pf-device-service --since 26h 2>&1 | grep -a 'Mozilla\|Chrome/\|Edge/' | tail -6 | cut -c1-450;
echo '===AIRPORT_BIND_POLL_LONG===';
SE docker logs tas-service --since 26h 2>&1 | grep -ac 'airport_bind_status';
SE docker logs tas-service --since 26h 2>&1 | grep -a 'airport_bind_status' | tail -2 | cut -c1-400;
echo '===DEVOPS_BE_LOGS===';
SE docker logs es310-privatization-devops-be --since 26h 2>&1 | grep -aiE 'bind|device' | tail -8 | cut -c1-450;
echo '===DEVOPS_FE_PORT===';
SE docker ps --format '{{.Names}} {{.Ports}}' 2>/dev/null | grep -aiE 'devops|frontend|lambda-nginx' | head -5;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
