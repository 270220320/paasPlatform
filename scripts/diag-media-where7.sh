#!/usr/bin/env bash
# 媒体位置诊断（七）：storage 最终对比（宽松正则）+ Redis 设备快照
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===DOCK_STORAGE_VALUES_60M===;
docker logs tas-service --since 60m 2>&1 | grep -a "thing/product/8UUXN2K00A0144/osd" | grep -ao "storage[^}]*}" | sort | uniq -c | sort -rn | head -5;
echo ===DRONE_STORAGE_VALUES_60M===;
docker logs tas-service --since 60m 2>&1 | grep -a "thing/product/1581F8HGX252500A009X/osd" | grep -ao "storage[^}]*}" | sort | uniq -c | sort -rn | head -5;
echo ===DRONE_IN_DOCK_LAST===;
docker logs tas-service --since 60m 2>&1 | grep -a "thing/product/8UUXN2K00A0144/osd" | grep -ao "drone_in_dock[^,]*" | sort | uniq -c;
echo ===REDIS_DEVICE_SNAPSHOT===;
docker exec redis redis-cli --no-raw GET common_310_device:8UUXN2K00A0144 2>/dev/null | grep -ao "storage[^}]*}\|media_file_detail[^}]*}\|drone_in_dock[^,]*" | head -5;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
