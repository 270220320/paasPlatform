#!/usr/bin/env bash
# 3.1升级后飞行任务失败排查（一）：设备绑定状态 + 司空航线库
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===DEVICE_LIST===';
curl -s -m 10 -H 'auth_keys: user_id' -H 'user_id: paas_test' "http://192.168.76.141:30812/gateway/device/api/v1/manager/devices?page_num=1&page_size=50" | head -c 1500;
echo;
echo '===WAYLINE_LIST===';
curl -s -m 10 -H 'auth_keys: user_id' -H 'user_id: paas_test' "http://192.168.76.141:30812/gateway/file/api/v1/waylines?page_num=1&page_size=50" | head -c 800;
echo;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
