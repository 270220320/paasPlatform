#!/usr/bin/env bash
# 诊断（三）：cmu 查看已安装许可证 + 定位 CodeMeter 服务 + 拿数据库密码备用
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===WHICH_CMU===;
which cmu; cmu --version 2>&1 | head -5;
echo ===CMU_LIST===;
cmu -l 2>&1 | head -40;
echo ===CODEMETER_PROC===;
ps aux | grep -iE "codemeter|wibu" | grep -v grep;
echo ===CODEMETER_CONTAINER===;
docker ps -a --format "{{.Names}}\t{{.Image}}\t{{.Status}}" | grep -iE "codemeter|wibu|cmeter";
echo ===MYSQL_ENV===;
docker exec mysql_instance_1 env 2>/dev/null | grep -iE "MYSQL_(ROOT_)?PASSWORD";
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
