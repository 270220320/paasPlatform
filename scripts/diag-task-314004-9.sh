#!/usr/bin/env bash
# 诊断（九）：traefik 访问日志 + 服务器本机 curl kmz URL + 找机场 IP
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
W1=2026-08-25T01:42:40; W2=2026-08-25T01:43:40;
echo ===TRAEFIK_30802===;
docker logs --since $W1 --until $W2 traefik 2>&1 | grep -a 30802 | head -20;
echo ===TRAEFIK_ACCESS_ANY_KMZ===;
docker logs --since $W1 --until $W2 traefik 2>&1 | grep -aiE "kmz|file-storage" | head -10;
echo ===CURL_KMZ_LOCAL===;
curl -s -o /dev/null -w "HTTP %{http_code} size %{size_download} time %{time_total}s\n" --max-time 15 "http://192.168.76.141:30802/file-storage-privatization/719d6171-a5db-4683-8d3f-2dce3c9afd5a/%E7%82%B9%E7%9A%84.kmz?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Checksum-Mode=ENABLED&X-Amz-Credential=admin%2F20260825%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260825T014242Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host&response-expires=Wed%2C%2026%20Aug%202026%2001%3A42%3A42%20GMT&x-id=GetObject&X-Amz-Signature=63a42fec92923dcd3d542fb2c6eb7d365a96318e3f8da82adaf60aab6d24f717";
echo ===EMQX_DOCK_IP===;
docker exec emqx emqx_ctl clients list 2>/dev/null | grep -aiE "8UUXN2K00A0144" | head -3;
echo ===NETSTAT_DOCK===;
netstat -an | grep -a :1883 | grep -a ESTABLISHED | head -10;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
