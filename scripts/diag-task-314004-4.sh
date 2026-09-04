#!/usr/bin/env bash
# 诊断（四）：司空 postgres 任务表结构+失败任务行（找机场上报的细粒度失败信息）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
PG=postgres_instance_1;
echo ===DB_LIST===;
docker exec $PG psql -U postgres -Atc "SELECT datname FROM pg_database WHERE NOT datistemplate;" 2>&1 | head -10;
echo ===FIND_TASKS_TABLE===;
for DB in $(docker exec $PG psql -U postgres -Atc "SELECT datname FROM pg_database WHERE NOT datistemplate;" 2>/dev/null); do
  T=$(docker exec $PG psql -U postgres -d $DB -Atc "SELECT tablename FROM pg_tables WHERE tablename='"'"'tasks'"'"';" 2>/dev/null);
  [ -n "$T" ] && echo "tasks in DB: $DB";
done;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
