#!/usr/bin/env bash
# 司空3.1升级（六）：排查 30887/30826/30827 端口占用
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===PORT_OCCUPY===';
echo 'Tksc@197653' | sudo -S ss -tlnp 2>/dev/null | grep -E ':(30887|30826|30827)\b';
echo '===PORT_ANY_STATE===';
echo 'Tksc@197653' | sudo -S ss -tanp 2>/dev/null | grep -E ':(30887|30826|30827)\b' | head -15;
echo '===FUSER_CHECK===';
for p in 30887 30826 30827; do
  echo "--- port $p ---";
  echo 'Tksc@197653' | sudo -S fuser $p/tcp 2>&1;
done;
echo '===PROC_DETAIL===';
for pid in $(echo 'Tksc@197653' | sudo -S ss -tlnp 2>/dev/null | grep -oE ':(30887|30826|30827)\b.*pid=[0-9]+' | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u); do
  echo "--- PID $pid ---";
  echo 'Tksc@197653' | sudo -S ps -fp $pid 2>/dev/null;
  echo 'Tksc@197653' | sudo -S ls -l /proc/$pid/cwd /proc/$pid/exe 2>/dev/null;
done;
echo '===DOCKER_PS_CHECK===';
echo "running_containers=$(echo 'Tksc@197653' | sudo -S docker ps -q 2>/dev/null | wc -l)";
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
