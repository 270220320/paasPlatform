#!/usr/bin/env bash
# 诊断（四）：enabled 容器许可证详情 + MySQL 中搜 license 表与 UUID a12f2ee2
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===CMU_DETAIL_ENABLED===;
cmu -x -s 130-1620695056 2>&1 | head -60;
echo ===MYSQL_DATABASES===;
docker exec mysql_instance_1 mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -e "SHOW DATABASES;" 2>/dev/null;
echo ===MYSQL_LICEN_TABLES===;
docker exec mysql_instance_1 mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -e "SELECT table_schema, table_name FROM information_schema.tables WHERE table_name REGEXP \"licen|authoriz|certificate\";" 2>/dev/null;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
