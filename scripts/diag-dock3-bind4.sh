#!/usr/bin/env bash
# 诊断第四阶段：数据库设备绑定状态 + 组织信息
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
M="docker exec mysql_instance_1 mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz";
echo ====DATABASES====;
$M -N -e "show databases" 2>/dev/null | grep -viE "information_schema|performance_schema|sys|mysql$";
echo ====DEVICE_TABLES====;
for d in $($M -N -e "show databases" 2>/dev/null | grep -viE "information_schema|performance_schema|^sys$|^mysql$"); do T=$($M -N -e "show tables from \`$d\` like \x27%devices%\x27" 2>/dev/null); [ -n "$T" ] && echo "[$d] $T"; done;
echo ====DEVICES====;
for d in devicesDB devices cloudapi es310 fh2; do R2=$($M -N -e "select sn,device_bind_status,device_online_status,active_prj_uuid from \`$d\`.devices where sn in (\x278UUXN2K00A0144\x27,\x271581F8HGX252500A009X\x27)" 2>/dev/null); [ -n "$R2" ] && echo "[${d}.devices]" && echo "$R2"; done;
echo ====APP_BINDINGS====;
for d in tas tasDB thing; do R3=$($M -N -e "select * from \`$d\`.device_app_bindings limit 5" 2>/dev/null); [ -n "$R3" ] && echo "[${d}.device_app_bindings]" && echo "$R3"; done'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
