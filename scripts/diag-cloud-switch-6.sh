#!/usr/bin/env bash
# 云服务切换失败排查（六）：定位 tcp://host:1883 所在文件并提取表单上下文
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===FILES_WITH_TCP===;
docker exec frontend sh -c "grep -rla tcp://host:1883 /app 2>/dev/null | head -6";
echo ===FILES_WITH_MQTT===;
docker exec frontend sh -c "grep -rla mqtt://host:1883 /app 2>/dev/null | head -6";
echo ===TCP_CONTEXT_LOOSE===;
for f in $(docker exec frontend sh -c "grep -rla tcp://host:1883 /app 2>/dev/null | head -3"); do
  echo "--- $f";
  docker exec frontend sh -c "grep -oaE .{80}tcp://host:1883.{80} $f 2>/dev/null" | head -3;
done;
echo ===MQTT_CONTEXT_LOOSE===;
for f in $(docker exec frontend sh -c "grep -rla mqtt://host:1883 /app 2>/dev/null | head -3"); do
  echo "--- $f";
  docker exec frontend sh -c "grep -oaE .{80}mqtt://host:1883.{80} $f 2>/dev/null" | head -3;
done;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
