#!/usr/bin/env bash
# 修复执行（十一）：重新开机无人机（为用户重新创建任务做准备）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
POST() { curl -s -m 100 -X POST 'http://127.0.0.1:38081/thing-access-service/inner/tas/devices/8UUXN2K00A0144/services' -H 'Content-Type: application/json' -d "{\"bid\":\"\",\"data\":$2,\"identifier\":\"$1\"}"; }
echo '===A_DEBUG_MODE_OPEN===';
date -u +%H:%M:%S;
POST debug_mode_open '{}' | head -c 300;
echo '';
sleep 5;
echo '===B_DRONE_OPEN===';
date -u +%H:%M:%S;
POST drone_open '{}' | head -c 300;
echo '';
echo '===C_POLL_ONLINE===';
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  sleep 15;
  ST=$(curl -s -m 10 -X POST 'http://127.0.0.1:38081/thing-access-service/inner/tas/topos/list' -H 'Content-Type: application/json' -d '{"sns":["8UUXN2K00A0144"]}' | grep -ao '"device_model":"drone"[^}]*online_status":\(true\|false\)' | grep -ao 'online_status":\(true\|false\)' | head -1);
  echo "poll$i ($(date -u +%H:%M:%S)) drone_online:[$ST]";
  echo "$ST" | grep -q true && { echo '*** DRONE ONLINE ***'; break; };
done;
echo '===D_DEBUG_MODE_CLOSE===';
POST debug_mode_close '{}' | head -c 300;
echo '';
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
