#!/usr/bin/env bash
# 诊断（二）：读 InstallLicense.sh 内容 + 安装包目录结构 + 数据库容器
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===INSTALL_LICENSE_SH===;
cat /opt/cloudApi2_3/fh2-privatization-install-packages/InstallLicense.sh;
echo ===PKG_DIR===;
ls -la /opt/cloudApi2_3/fh2-privatization-install-packages/ | head -40;
echo ===DB_CONTAINERS===;
docker ps --format "{{.Names}}\t{{.Image}}" | grep -iE "mysql|postgres|mongo|redis|traefik|emqx|minio";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
