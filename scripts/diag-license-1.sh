#!/usr/bin/env bash
# 诊断（一）：在司空服务器上搜索许可证序列号 a12f2ee2-288f-4580-95e2-21b8dd750463 的安装痕迹
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
SN=a12f2ee2-288f-4580-95e2-21b8dd750463;
echo ===GREP_INSTALL_DIR===;
grep -rl --binary-files=without-match --exclude=*.tar --exclude=*.tgz --exclude=*.zip --exclude=*.gz --exclude=*.img --exclude=*.iso "$SN" /opt/cloudApi2_3 2>/dev/null | head -30;
echo ===FIND_LIC_FILES===;
find /opt/cloudApi2_3 \( -iname "*.lic" -o -iname "*license*" -o -iname "*licence*" \) 2>/dev/null | grep -viE "node_modules|\.js$|\.js\.map$|\.ts$" | head -30;
echo ===DOCKER_PS===;
docker ps --format "{{.Names}}\t{{.Image}}\t{{.Status}}" 2>/dev/null | head -40;
echo ===REDIS_SCAN_SN===;
docker exec redis redis-cli -n 0 --scan --pattern "*a12f2ee2*" 2>/dev/null | head -10;
echo ===REDIS_SCAN_LICEN===;
docker exec redis redis-cli -n 0 --scan --pattern "*licen*" 2>/dev/null | head -20;
echo ===GREP_HOME_ETC===;
grep -rl --binary-files=without-match --exclude=*.tar --exclude=*.tgz --exclude=*.gz "$SN" /home /etc 2>/dev/null | head -10;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
