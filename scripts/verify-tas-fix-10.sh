#!/usr/bin/env bash
# 修复验证（十六）：确认任务终态 + 无人机回舱状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
S() { echo 'Tksc@197653' | sudo -S "$@" 2>/dev/null; }
SE() { echo 'Tksc@197653' | sudo -S "$@" 2>&1; }
echo '===A_TASK_FINAL===';
S docker exec postgres_instance_1 psql -U app_rw -d es_task -c "SELECT uuid,name,status,drone_sn,begin_at,end_at,completed_at FROM tasks WHERE uuid='42bfc12c-7245-47bd-bb9f-8b18b4b1dc69'" 2>&1 | head -8;
echo '===B_TASK_END_LOG===';
SE docker logs es-task --since 8m 2>&1 | grep -a '42bfc12c' | grep -aiE 'task end|finish|completed|land|回舱|flighttask_finish' | grep -av 'track-id already' | cut -c1-350 | tail -6;
echo '===C_LAST_MISSION_STATE===';
SE docker logs es-task --since 8m 2>&1 | grep -a 'wayline_mission_state' | grep -ao 'wayline_mission_state=[0-9]*' | sort | uniq -c;
echo '===D_DOCK_STATUS_NOW===';
SE docker logs tas-service --since 2m 2>&1 | grep -a '"topic":"thing/product/8UUXN2K00A0144/osd"' | tail -1 | cut -c1-600 | grep -aoE 'drone_in_dock[^,]*|cover_state[^,]*|drone_charge_state[^}]*}' | head -5;
echo '===E_DRONE_OSD_LAST===';
SE docker logs tas-service --since 5m 2>&1 | grep -ac '"topic":"thing/product/1581F8HGX252500A009X/osd"';
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
