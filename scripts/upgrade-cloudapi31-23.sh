#!/usr/bin/env bash
# 司空3.1升级（二十三）：Postgres/Mongo 定位飞行计划表
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===PG1_DBS===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 sh -c 'psql -U app_rw -d postgres -Atc "SELECT datname FROM pg_database WHERE NOT datistemplate"' 2>&1;
echo '===PG2_DBS===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c 'psql -U app_rw -d postgres -Atc "SELECT datname FROM pg_database WHERE NOT datistemplate"' 2>&1;
echo '===PG1_WAYLINE_SEARCH===';
for db in $(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 sh -c 'psql -U app_rw -d postgres -Atc "SELECT datname FROM pg_database WHERE NOT datistemplate"' 2>/dev/null); do
  T=$(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 sh -c "psql -U app_rw -d $db -Atc \"SELECT tablename FROM pg_tables WHERE tablename ~* 'wayline|flight|plan|route' LIMIT 15\"" 2>/dev/null);
  [ -n "$T" ] && echo "--- pg1/$db ---" && echo "$T";
done;
echo '===PG2_WAYLINE_SEARCH===';
for db in $(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c 'psql -U app_rw -d postgres -Atc "SELECT datname FROM pg_database WHERE NOT datistemplate"' 2>/dev/null); do
  T=$(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U app_rw -d $db -Atc \"SELECT tablename FROM pg_tables WHERE tablename ~* 'wayline|flight|plan|route' LIMIT 15\"" 2>/dev/null);
  [ -n "$T" ] && echo "--- pg2/$db ---" && echo "$T";
done;
echo '===MONGO_TRY===';
echo 'Tksc@197653' | sudo -S docker exec mongo sh -c 'mongo --quiet --eval "db.adminCommand({listDatabases:1}).databases.forEach(function(d){print(d.name)})" 2>&1 || mongosh --quiet --eval "db.adminCommand({listDatabases:1}).databases.forEach(function(d){print(d.name)})" 2>&1' | head -15;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
