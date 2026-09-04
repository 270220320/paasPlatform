#!/usr/bin/env bash
# 云服务切换失败排查（八）：把 demo JS 拉回本地分析
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 \
  "echo 'Tksc@197653' | sudo -S docker exec frontend cat /app/paasjs-demo/device-manager-sdk-v2/index.js | base64 -w0" 2>/dev/null > /tmp/dm_sdk2.js.b64
ls -la /tmp/dm_sdk2.js.b64
base64 -d /tmp/dm_sdk2.js.b64 > /tmp/dm_sdk2.js
wc -c /tmp/dm_sdk2.js
