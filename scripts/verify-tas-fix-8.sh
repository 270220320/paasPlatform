#!/usr/bin/env bash
# 修复验证（十四）：任务 42bfc12c 实时飞行状态确认
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_DRONE_OSD_NOW===';
SE docker logs tas-service --since 1m 2>&1 | grep -a '"topic":"thing/product/1581F8HGX252500A009X/osd"' | tail -1 | grep -aoE '"elevation":[0-9.]*|"latitude":[0-9.]*|"longitude":[0-9.]*|"capacity_percent":[0-9]*|"mode_code":[0-9]*|"height":[0-9.]*' | head -12;
echo '===B_TASK_PROGRESS_EVENTS===';
SE docker logs tas-service --since 2m 2>&1 | grep -a 'flighttask_progress' | tail -2 | grep -aoE '"status":"[a-z_]*"|"percent":[0-9]*|"current_step":[0-9]*' | head -10;
echo '===C_FLIGHT_TASK_DB===';
P=$(S docker exec postgres_instance_1 printenv POSTGRES_USER);
S docker exec postgres_instance_1 psql -U ${P:-app_rw} -d es_task -x -c "SELECT flight_task_id,task_name,task_status,flight_task_status,task_type,created_at,updated_at FROM flight_tasks WHERE flight_task_id='42bfc12c-7245-47bd-bb9f-8b18b4b1dc69'" 2>&1 | head -14;
echo '===D_WAYPOINT_NOW===';
SE docker logs tas-service --since 1m 2>&1 | grep -a 'current_waypoint_index' | tail -1 | grep -aoE '"current_waypoint_index":[0-9]*|"track_id":"[a-f0-9-]*"|break_point[^,]*' | head -5;
echo '===E_ESTASK_TASK_STATE===';
SE docker logs es-task --since 2m 2>&1 | grep -a '42bfc12c' | grep -aiE 'status|state|progress|finish|complete|ok' | grep -av 'track-id already' | cut -c1-350 | tail -6;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
