#!/usr/bin/env bash
# 修复验证（十五）：任务终态与无人机当前状态（放宽窗口）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_MISSION_STATE_SEQ_5M===';
SE docker logs es-task --since 5m 2>&1 | grep -a 'wayline_mission_state' | grep -ao '"time":"[^"]*"\|wayline_mission_state=[0-9]*\|taskUUID=[a-f0-9-]*' | paste - - - 2>/dev/null | tail -12;
echo '===B_DRONE_OSD_5M===';
SE docker logs tas-service --since 5m 2>&1 | grep -a '"topic":"thing/product/1581F8HGX252500A009X/osd"' | tail -1 | grep -aoE '"elevation":[0-9.]*|"latitude":[0-9.]{8,}|"longitude":[0-9.]{8,}|"capacity_percent":[0-9]*|"mode_code":[0-9]*' | head -8;
echo '===C_DOCK_OSD_5M===';
SE docker logs tas-service --since 5m 2>&1 | grep -a '"topic":"thing/product/8UUXN2K00A0144/osd"' | tail -1 | grep -aoE '"drone_in_dock":[0-9]*|"cover_state":[0-9]*|"mode_code":[0-9]*' | head -5;
echo '===D_TASKS_TABLE===';
S docker exec postgres_instance_1 psql -U app_rw -d es_task -c "SELECT uuid,name,status,sn,drone_sn,begin_at,end_at,completed_at FROM tasks WHERE uuid='42bfc12c-7245-47bd-bb9f-8b18b4b1dc69'" 2>&1 | head -10;
echo '===E_FLIGHT_TASKS_TABLE===';
S docker exec postgres_instance_1 psql -U app_rw -d es_task -c "SELECT flight_task_id,task_name,task_status,flight_task_status,updated_at FROM flight_tasks WHERE flight_task_id='42bfc12c-7245-47bd-bb9f-8b18b4b1dc69'" 2>&1 | head -10;
echo '===F_WAYLINE_STATE_ENUM_LOGS===';
SE docker logs es-task --since 6m 2>&1 | grep -a '42bfc12c' | grep -aiE 'sendTaskExecute|flighttask_execute|task end|finish|land|return' | grep -av 'track-id already' | cut -c1-300 | tail -8;
echo '===G_PROGRESS_LAST===';
SE docker logs tas-service --since 4m 2>&1 | grep -a 'flighttask_progress' | tail -1 | cut -c1-400;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
