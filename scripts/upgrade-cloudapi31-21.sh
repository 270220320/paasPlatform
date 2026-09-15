#!/usr/bin/env bash
# 司空3.1升级（二十一）：侦察飞行计划库数据位置与数量
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===PG1_DBS===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 sh -c 'psql -U postgres -Atc "SELECT datname FROM pg_database WHERE NOT datistemplate"' 2>/dev/null;
echo '===PG2_DBS===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c 'psql -U postgres -Atc "SELECT datname FROM pg_database WHERE NOT datistemplate"' 2>/dev/null;
echo '===PG1_WAYLINE_TABLES===';
for db in $(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 sh -c 'psql -U postgres -Atc "SELECT datname FROM pg_database WHERE NOT datistemplate"' 2>/dev/null); do
  echo "--- pg1/$db ---";
  echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 sh -c "psql -U postgres -d $db -Atc \"SELECT tablename FROM pg_tables WHERE tablename ~* 'wayline|flight_plan|plan' LIMIT 20\"" 2>/dev/null;
done;
echo '===PG2_WAYLINE_TABLES===';
for db in $(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c 'psql -U postgres -Atc \"SELECT datname FROM pg_database WHERE NOT datistemplate\"' 2>/dev/null); do
  echo "--- pg2/$db ---";
  echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 sh -c "psql -U postgres -d $db -Atc \"SELECT tablename FROM pg_tables WHERE tablename ~* 'wayline|flight_plan|plan' LIMIT 20\"" 2>/dev/null;
done;
echo '===MINIO_BUCKETS===';
echo 'Tksc@197653' | sudo -S docker exec minio sh -c 'ls /data/ 2>/dev/null || mc ls local 2>/dev/null' 2>&1 | head -10;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
