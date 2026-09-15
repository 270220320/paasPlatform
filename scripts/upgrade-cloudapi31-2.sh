#!/usr/bin/env bash
# 司空3.1升级（二）：compose项目清单 + 卷快照备份
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===COMPOSE_PROJECTS===';
echo 'Tksc@197653' | sudo -S docker compose ls 2>/dev/null;
echo '===VOLUME_SNAPSHOT===';
echo 'Tksc@197653' | sudo -S docker volume ls --format "{{.Name}}" 2>/dev/null > /home/test/volumes_before_31_upgrade.txt;
wc -l /home/test/volumes_before_31_upgrade.txt;
echo '===PS_SNAPSHOT===';
echo 'Tksc@197653' | sudo -S docker ps --format "{{.Names}}|{{.Status}}" 2>/dev/null > /home/test/containers_before_31_upgrade.txt;
wc -l /home/test/containers_before_31_upgrade.txt;
echo '===COMPOSE_VERSION===';
docker compose version;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
