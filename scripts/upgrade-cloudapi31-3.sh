#!/usr/bin/env bash
# 司空3.1升级（三）：停掉3.0两个compose项目（保留数据卷）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===DOWN_FLIGHTHUB2===';
echo 'Tksc@197653' | sudo -S docker compose -p flighthub2 -f /opt/cloudApi2_3/install/compose.yaml --project-directory /opt/cloudApi2_3/install down 2>&1 | tail -5;
echo '===DOWN_FH2_SYSTEM===';
echo 'Tksc@197653' | sudo -S docker compose -p fh2-system -f /opt/cloudApi2_3/es-privatization-system/compose_paas.yaml --project-directory /opt/cloudApi2_3/es-privatization-system down 2>&1 | tail -5;
echo '===VERIFY_PS===';
echo 'Tksc@197653' | sudo -S docker ps --format "{{.Names}}" 2>/dev/null | head -10;
echo "remaining_containers=$(echo 'Tksc@197653' | sudo -S docker ps -q 2>/dev/null | wc -l)";
echo '===VERIFY_COMPOSE_LS===';
echo 'Tksc@197653' | sudo -S docker compose ls 2>/dev/null;
echo '===VERIFY_VOLUMES===';
echo "volume_count=$(echo 'Tksc@197653' | sudo -S docker volume ls -q 2>/dev/null | wc -l)";
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
