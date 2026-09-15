#!/usr/bin/env bash
# 3.1升级后飞行任务失败排查（四）：找pg容器名 + 任务详情
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===PG_CONTAINERS===';
echo 'Tksc@197653' | sudo -S docker ps --format '{{.Names}}' 2>/dev/null | grep -i postgres;
echo '===ES_TASK_IN_PG===';
for c in $(sudo docker ps --format '{{.Names}}' | grep -i postgres); do
  echo "-- container=$c";
  echo 'Tksc@197653' | sudo -S docker exec $c psql -U app_rw -l 2>/dev/null | grep -E 'es_task|file_storage|Name' | head -5;
done;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
