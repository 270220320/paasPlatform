#!/usr/bin/env bash
# 挖网关路由注册：traefik 配置来源 + redis 路由键 + es-task 服务端口
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo "=== A) all containers (name/ports) ==="
docker ps --format '{{.Names}} | {{.Ports}}' | sort
echo "=== B) es-task listen ports (netstat in container ns) ==="
PID=$(docker inspect -f '{{.State.Pid}}' es-task)
nsenter -t $PID -n ss -tlnp 2>/dev/null | head -10
echo "=== C) redis keys with task/route ==="
docker exec redis redis-cli keys '*task*' 2>/dev/null | head -20
docker exec redis redis-cli keys '*route*' 2>/dev/null | head -20
echo "=== D) traefik static config providers ==="
docker exec traefik sh -c 'cat /etc/traefik/traefik.yml 2>/dev/null || cat /etc/traefik/traefik.toml 2>/dev/null' | head -40
echo "=== E) traefik entrypoint route rules from redis provider ==="
docker exec redis redis-cli --scan 2>/dev/null | grep -aiE 'traefik' | head -30
echo DONE
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo Tksc@197653 | sudo -S bash -c 'echo $R | base64 -d | bash'"
