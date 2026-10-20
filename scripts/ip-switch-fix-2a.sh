#!/usr/bin/env bash
# 2026-09-21 IP 切换修复2a：司空 MySQL 检查库表中的 76.141 残留
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
cat > /tmp/q1.sql <<'EOF'
SELECT CONCAT(table_schema,'.',table_name) FROM information_schema.tables
WHERE table_schema NOT IN ('information_schema','performance_schema','mysql','sys')
AND (table_name LIKE '%map%' OR table_name LIKE '%config%' OR table_name LIKE '%setting%' OR table_name LIKE '%media%' OR table_name LIKE '%oss%' OR table_name LIKE '%storage%')
ORDER BY 1;
EOF
cat > /tmp/q2.sql <<'EOF'
SELECT table_schema, table_name, column_name, data_type FROM information_schema.columns
WHERE table_schema NOT IN ('information_schema','performance_schema','mysql','sys')
AND (column_name LIKE '%link%' OR column_name LIKE '%url%' OR column_name LIKE '%host%' OR column_name LIKE '%endpoint%' OR column_name LIKE '%address%')
ORDER BY table_schema, table_name LIMIT 60;
EOF
echo "===== 1. candidate tables ====="
echo "Tksc@197653" | sudo -S docker exec -i mysql_instance_1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B' < /tmp/q1.sql 2>/dev/null
echo "===== 2. candidate url/link columns ====="
echo "Tksc@197653" | sudo -S docker exec -i mysql_instance_1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B' < /tmp/q2.sql 2>/dev/null
echo "===== 3. all business schemas ====="
cat > /tmp/q3.sql <<'EOF'
SELECT schema_name FROM information_schema.schemata WHERE schema_name NOT IN ('information_schema','performance_schema','mysql','sys');
EOF
echo "Tksc@197653" | sudo -S docker exec -i mysql_instance_1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B' < /tmp/q3.sql 2>/dev/null
echo "===== FIX2a DONE ====="
REMOTE
