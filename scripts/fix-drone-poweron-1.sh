#!/usr/bin/env bash
# 修复执行（一）：拉 CreateService 完整 body 格式 + 确认 tas 直连端口
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_FULL_POST_SERVICES_LINE===';
SE docker logs tas-service --since 3h 2>&1 | grep -a 'devices/8UUXN2K00A0144/services' | head -2 > /tmp/svc_line.txt;
wc -c /tmp/svc_line.txt;
cat /tmp/svc_line.txt | head -c 4000;
echo '';
echo '===B_TAS_PORT_ENV===';
S docker inspect tas-service --format '{{json .Config.Env}}' 2>/dev/null | tr ',' '\n' | grep -aiE 'port|addr|listen' | head;
S docker inspect tas-service --format '{{json .NetworkSettings.Ports}}' 2>/dev/null;
echo '===C_TAS_NETWORKS===';
S docker inspect tas-service --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{$v.IPAddress}} {{end}}' 2>/dev/null;
echo '===D_TRAEFIK_38081_HOSTPORT===';
S docker ps --format '{{.Names}} {{.Ports}}' 2>/dev/null | grep -aiE 'traefik|tas' | head -5;
echo '===E_INNER_AUTH_CHECK===';
S docker exec es-task sh -c 'wget -qO- --timeout=4 http://tas-service:8080/thing-access-service/inner/tas/devices/properties/8UUXN2K00A0144 2>&1 | head -c 300' 2>/dev/null || echo TRY_8080_FAIL;
echo '';
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
