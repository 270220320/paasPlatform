#!/usr/bin/env bash
# 诊断第三阶段：BindDevice 完整流程日志 + 数据库设备绑定状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ====MYSQL_ENV====;
docker inspect mysql_instance_1 --format "{{json .Config.Env}}" | tr "," "\n" | grep -iE "MYSQL|DATABASE" ;
echo ====TAS_BIND_FLOW====;
docker logs --since 6h tas-service 2>&1 | grep -E "8UUXN2K00A0144|BindDevice|bind" | tail -30 ;
echo ====TAS_EMQX====;
docker logs --since 6h tas-emqx-service 2>&1 | grep -iE "8UUXN2K00A0144|error|bind" | tail -20 ;
echo ====ES_PRIV_SYS_BIND====;
docker logs --since 6h es-privatization-system 2>&1 | grep -iE "8UUXN2K00A0144|1581F8HGX252500A009X" | grep -iE "bind|org|error" | tail -15'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
