#!/usr/bin/env bash
# 诊断（四b）：查 postgres 容器用户与库，定位 tasks 表
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
for PG in postgres_instance_1 postgres_instance_2; do
  echo "===$PG ENV===";
  docker inspect $PG --format "{{range .Config.Env}}{{println .}}{{end}}" | grep -aE "POSTGRES_(USER|DB)";
done;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
