#!/usr/bin/env bash
# 219015 排查（十一）：下游服务谁返回 "task type unsupported" + es-task tasks 表实际值
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
echo '===WHO_SAYS_TASK_TYPE===';
for c in tas-service pf-control-core es-drc-service ts-gateway drtc-manager es-management; do
  cnt=$(S docker logs "$c" --since 24h 2>&1 | grep -ac 'task type');
  echo "--- $c => $cnt";
  S docker logs "$c" --since 24h 2>&1 | grep -a 'task type' | head -3 | cut -c1-500;
done;
echo '===TAS_A16AC05D===';
S docker logs tas-service --since 24h 2>&1 | grep -a 'a16ac05d' | head -10 | cut -c1-600;
echo '===PG_LIST_DB===';
S docker exec postgres_instance_1 psql -U postgres -tAc "SELECT datname FROM pg_database" 2>&1 | head -20;
echo '===ES_TASK_DB_TASKS_TABLE===';
S docker exec postgres_instance_1 psql -U postgres -d es_task -x -c "SELECT * FROM tasks ORDER BY created_at DESC LIMIT 1" 2>&1 | head -60;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
