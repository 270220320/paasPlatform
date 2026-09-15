#!/usr/bin/env bash
# 修复验证（九）：219032 device get property error 精确定位 + task type unsupported 残留 + tas property 链路
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_TASKTYPE_UNSUP_LINES_20M===';
SE docker logs es-task --since 20m 2>&1 | grep -a 'task type unsupported' | grep -ao '"time":"[^"]*"' | sort | uniq -c;
echo '===B_219032_LINES_20M===';
SE docker logs es-task --since 20m 2>&1 | grep -a '219032' | grep -ao '"time":"[^"]*"' | sort | uniq -c;
echo '===C_ALL_EXCEPTIONS_20M===';
SE docker logs es-task --since 20m 2>&1 | grep -a 'publish exceptions' | cut -c1-450 | tail -6;
echo '===D_TASK_WINDOW_FULL===';
SE docker logs es-task --since 20m --until 0s 2>&1 | grep -a '03:03:2' | grep -av 'relay_missions' | grep -aiE 'ready|property|cloudbus|Handle|create|precheck|check|model' | cut -c1-500 | head -30;
echo '===E_TAS_WINDOW_0303===';
SE docker logs tas-service --since 20m 2>&1 | grep -a '03:03:2' | grep -av '"topic":"thing/product/8UUXN2K00A0144/osd"' | grep -av 'device_osd' | cut -c1-450 | head -40;
echo '===F_TAS_PROPERTY_KEYWORDS_20M===';
SE docker logs tas-service --since 20m 2>&1 | grep -aiE 'property|get_prop' | grep -av 'osd' | cut -c1-400 | tail -15;
echo '===G_TAS_DICT_NOW===';
MY() { docker exec mysql_instance_1 mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e "$1" 2>/dev/null; }
echo '--- device_models all ---';
S docker exec mysql_instance_1 sh -c "mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz -N -e 'SELECT type,category,device_name FROM ThingAccessService.device_models ORDER BY type' " 2>/dev/null;
echo '===H_TAS_BIN_CATEGORY_ENUM===';
S docker exec tas-service sh -c "tr -c '[:print:]' '\n' < /app/main 2>/dev/null | grep -axE '(dock|airport|rc|drone|camera|payload)' | sort | uniq -c" 2>/dev/null;
echo '===I_TASK_0303_TRACE===';
SE docker logs es-task --since 20m 2>&1 | grep -a 'trace_id":"5ba9372e1a551eb089ff02ff3eda03a7' | grep -av 'UPDATE\|SELECT\|relay' | cut -c1-400 | head -40;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
