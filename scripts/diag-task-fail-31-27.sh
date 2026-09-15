#!/usr/bin/env bash
# 219015 排查（十二）：WARN 前后完整日志 + 全容器扫错误串 + PG 连接参数
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===ES_TASK_092850_FULL===';
SE docker logs es-task --since 24h 2>&1 | grep -a '09:28:50' | grep -av '"level":"info","msg":"\[' | cut -c1-1600;
echo '===ALL_CONTAINERS_ERR_SCAN===';
for c in $(S docker ps --format '{{.Names}}'); do
  cnt=$(SE docker logs "$c" --since 24h 2>&1 | grep -ac 'task type unsupported');
  if [ "$cnt" != "0" ]; then echo "$c => $cnt"; fi;
done;
echo '(scan done)';
echo '===PG_ENV===';
SE docker exec postgres_instance_1 env | grep -i postgres;
echo '===PG_DBS===';
SE docker exec postgres_instance_1 psql -U es310 -tAc "SELECT datname FROM pg_database" 2>&1 | head -20;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
