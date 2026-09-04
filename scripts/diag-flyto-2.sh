#!/usr/bin/env bash
# 上传 flyto 诊断脚本 v2 到 76.141 并执行
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
cp /mnt/d/AiFlow/scripts/diag-flyto-remote2.sh /tmp/diag-flyto-remote2.sh
chmod +x /tmp/fh_askpass.sh
sed -i 's/\r$//' /tmp/diag-flyto-remote2.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w scp -o StrictHostKeyChecking=no /tmp/diag-flyto-remote2.sh test@192.168.76.141:/tmp/diag-flyto-remote2.sh 2>&1
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 'bash /tmp/diag-flyto-remote2.sh' 2>&1
