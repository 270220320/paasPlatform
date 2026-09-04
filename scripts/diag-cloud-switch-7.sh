#!/usr/bin/env bash
# 云服务切换失败排查（七）：thirdForm/api2Form 的提交接口与 body 构造
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
F=/app/paasjs-demo/device-manager-sdk-v2/index.js;
echo ===THIRDFORM_SUBMIT_CTX===;
docker exec frontend sh -c "grep -oaE .{50}thirdForm\.mqttAddr.{600} $F" | head -2;
echo ===API2FORM_FIELDS===;
docker exec frontend sh -c "grep -oaE api2Form:\{.{700} $F" | head -1;
echo ===THIRDFORM_FIELDS===;
docker exec frontend sh -c "grep -oaE thirdForm:\{.{700} $F" | head -1;
echo ===SWITCH_CALLS===;
docker exec frontend sh -c "grep -oaE .{200}(cloud-environment-switch|cloud_environment_switch).{300} $F" | head -3;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
