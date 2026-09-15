#!/usr/bin/env bash
# 司空3.1升级（二十二）：获取数据库凭证 + 定位飞行计划表
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===PG1_ENV===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 env 2>/dev/null | grep -E 'POSTGRES_(USER|DB|PASSWORD)';
echo '===PG2_ENV===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 env 2>/dev/null | grep -E 'POSTGRES_(USER|DB|PASSWORD)';
echo '===MYSQL_ENV===';
echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 env 2>/dev/null | grep -iE 'MYSQL_(ROOT|USER|DATABASE|PASSWORD)';
echo '===MYSQL_DBS===';
MPASS=$(echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 printenv MYSQL_ROOT_PASSWORD 2>/dev/null);
echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 sh -c "mysql -uroot -p'$MPASS' -N -e 'show databases'" 2>/dev/null;
echo '===MYSQL_WAYLINE_TABLES===';
for db in $(echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 sh -c "mysql -uroot -p'$MPASS' -N -e 'show databases'" 2>/dev/null | grep -viE 'information_schema|performance_schema|mysql|sys'); do
  echo "--- mysql/$db ---";
  echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 sh -c "mysql -uroot -p'$MPASS' -N -e \"SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema='$db' AND table_name REGEXP 'wayline|flight|plan'\"" 2>/dev/null;
done;
echo '===MONGO_DBS===';
echo 'Tksc@197653' | sudo -S docker exec mongo sh -c 'mongosh --quiet --eval "db.adminCommand({listDatabases:1}).databases.forEach(d=>print(d.name))"' 2>/dev/null;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
