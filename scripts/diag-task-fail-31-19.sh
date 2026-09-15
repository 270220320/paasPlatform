#!/usr/bin/env bash
# 219015 排查（四）：找atc容器 + 它对该PUT的日志 + 页面create对照准备
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===ATC_CONTAINER===';
echo 'Tksc@197653' | sudo -S docker ps --format '{{.Names}}' 2>/dev/null | grep -aiE 'atc|task|flight' | head -6;
echo '===ATC_LOG_219015===';
for c in $(echo 'Tksc@197653' | sudo -S docker ps --format '{{.Names}}' 2>/dev/null | grep -aiE 'atc'); do
  echo "-- $c";
  echo 'Tksc@197653' | sudo -S docker logs $c --since 3h 2>&1 | grep -a "219015" | tail -4;
done;
echo '===WHO_HANDLES_PUT_STATUS===';
for c in $(echo 'Tksc@197653' | sudo -S docker ps --format '{{.Names}}' 2>/dev/null | grep -aiE 'atc|wayline|es-task|flight|tas'); do
  hits=$(echo 'Tksc@197653' | sudo -S docker logs $c --since 3h 2>&1 | grep -ac "flight-tasks/.*/status");
  echo "$c status_put_hits=$hits";
done;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
