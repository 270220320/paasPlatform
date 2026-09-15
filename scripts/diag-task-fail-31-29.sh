#!/usr/bin/env bash
# 219015 排查（十四）：设备模型/topo 数据取证——tas MySQL devices 表 + 子设备关系
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===MYSQL_ENV===';
SE docker exec mysql_instance_1 env | grep -i mysql;
echo '===MYSQL_DBS===';
SE docker exec mysql_instance_1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SHOW DATABASES"' 2>&1 | head -25;
echo '===FIND_DEVICES_TABLE===';
SE docker exec mysql_instance_1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT table_schema,table_name FROM information_schema.tables WHERE table_name LIKE \"%device%\" LIMIT 20"' 2>&1;
echo '===DEVICE_ROW===';
SE docker exec mysql_instance_1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT table_schema FROM information_schema.tables WHERE table_name=\"devices\" LIMIT 5"' 2>&1 | while read db; do
  echo "--- db=$db";
  SE docker exec mysql_instance_1 sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -N -e \"SELECT * FROM \`$db\`.devices WHERE sn='8UUXN2K00A0144'\\\\G\"" 2>&1 | head -40;
done;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
