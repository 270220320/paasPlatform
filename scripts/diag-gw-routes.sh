#!/usr/bin/env bash
# 读 FlexNet 网关 Redis 路由表 le:*，找 task-management 的真实后端与路径规则
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo "=== A) all le:* keys ==="
docker exec redis redis-cli --scan --pattern 'le:*' 2>/dev/null | sort
echo "=== B) type+value of le:task-management ==="
docker exec redis redis-cli type le:task-management
docker exec redis redis-cli lrange le:task-management 0 -1 2>/dev/null
docker exec redis redis-cli get le:task-management 2>/dev/null
docker exec redis redis-cli smembers le:task-management 2>/dev/null
echo "=== C) le:wayline / le:safety values (对照限飞区已知路由) ==="
for k in le:wayline le:safety le:flighttask le:media; do
  echo "-- $k:"; docker exec redis redis-cli lrange $k 0 -1 2>/dev/null || true
done
echo DONE
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo Tksc@197653 | sudo -S bash -c 'echo $R | base64 -d | bash'"
