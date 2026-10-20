#!/usr/bin/env bash
# 2026-09-21 IP 切换修复2b：司空 MySQL 检查（修正 sudo -S 与 stdin 冲突：先 sudo -v 缓存凭证）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "Tksc@197653" | sudo -S -v 2>/dev/null
cat > /tmp/q1.sql <<'EOF'
SELECT CONCAT(table_schema,'.',table_name) FROM information_schema.tables
WHERE table_schema NOT IN ('information_schema','performance_schema','mysql','sys')
AND (table_name LIKE '%map%' OR table_name LIKE '%config%' OR table_name LIKE '%setting%' OR table_name LIKE '%media%' OR table_name LIKE '%oss%' OR table_name LIKE '%storage%')
ORDER BY 1;
EOF
cat > /tmp/q2.sql <<'EOF'
SELECT table_schema, table_name, column_name FROM information_schema.columns
WHERE table_schema NOT IN ('information_schema','performance_schema','mysql','sys')
AND (column_name LIKE '%link%' OR column_name LIKE '%url%' OR column_name LIKE '%host%' OR column_name LIKE '%endpoint%' OR column_name LIKE '%address%')
ORDER BY table_schema, table_name LIMIT 80;
EOF
cat > /tmp/q3.sql <<'EOF'
SELECT schema_name FROM information_schema.schemata WHERE schema_name NOT IN ('information_schema','performance_schema','mysql','sys');
EOF
echo "===== 3. all business schemas ====="
sudo docker exec -i mysql_instance_1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B' < /tmp/q3.sql
echo "===== 1. candidate tables ====="
sudo docker exec -i mysql_instance_1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B' < /tmp/q1.sql
echo "===== 2. candidate url/link columns ====="
sudo docker exec -i mysql_instance_1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B' < /tmp/q2.sql
echo "===== FIX2b DONE ====="
REMOTE
