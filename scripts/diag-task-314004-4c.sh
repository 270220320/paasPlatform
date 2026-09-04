#!/usr/bin/env bash
# 诊断（四c）：tasks 表结构 + 4 个失败任务全字段
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
PGU=app_rw;
for PG in postgres_instance_1 postgres_instance_2; do
  for DB in $(docker exec $PG psql -U $PGU -Atc "SELECT datname FROM pg_database WHERE NOT datistemplate;" 2>/dev/null); do
    C=$(docker exec $PG psql -U $PGU -d $DB -Atc "SELECT count(*) FROM pg_tables WHERE tablename='"'"'tasks'"'"';" 2>/dev/null);
    if [ "$C" = "1" ]; then echo "FOUND $PG $DB";
      docker exec $PG psql -U $PGU -d $DB -Atc "SELECT column_name FROM information_schema.columns WHERE table_name='"'"'tasks'"'"' ORDER BY ordinal_position;" | tr "\n" " ";
      echo; echo ===ROWS===;
      docker exec $PG psql -U $PGU -d $DB -x -c "SELECT * FROM tasks WHERE uuid IN ('"'"'fae02bc8-b72a-4540-85d2-4dd0526ff061'"'"','"'"'03b476d3-b6ff-43d6-998b-c1e586f132e5'"'"','"'"'b4229acc-ac6c-47bf-87e0-3bcabc50ea9b'"'"','"'"'45958205-ac05-4bb5-abba-b38dc625e326'"'"');" 2>&1 | grep -avE "Updated_at|updated_at|-[0-9]{2} rows|^\s*$" | head -120;
    fi;
  done;
done;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
