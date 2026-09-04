#!/usr/bin/env bash
# 媒体诊断（八）：MinIO 实时连接（机场IP 192.168.76.118）+ 上传流量探测 + es-management 媒体路由
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===MINIO_ESTABLISHED_FROM_DOCK===;
docker exec minio sh -c "cat /proc/net/tcp | awk \"\\\$4==\\\"01\\\" {print \\\$3}\" | sort | uniq -c | sort -rn | head -12" 2>/dev/null;
echo ===HOST_CONN_TO_30802===;
ss -tn state established "( sport = :30802 )" 2>/dev/null | head -10;
echo ===TRAEFIK_10MIN_PUT===;
docker logs traefik --since 10m 2>&1 | grep -aE "PUT|POST|GET" | grep -a "file-storage" | tail -6;
echo ===TRAEFIK_10MIN_ALL_30802===;
docker logs traefik --since 10m 2>&1 | grep -a "30802" | tail -6;
echo ===STORAGE_CONFIG_FREQ===;
docker logs traefik --since 6m 2>&1 | grep -ac "storage_config_get";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
