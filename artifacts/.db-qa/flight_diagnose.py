import subprocess, shlex, json
def run(args):
 p=subprocess.run(args,text=True,capture_output=True,timeout=35)
 if p.returncode: raise RuntimeError(p.stderr[:500])
 return p.stdout.strip()
def pg(db,sql):
 return run(['docker','exec','postgres_instance_1','sh','-c','PGPASSWORD="$POSTGRES_PASSWORD" exec psql -X -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d '+shlex.quote(db)+' -At -c '+shlex.quote(sql)])
queries={
 'latest_tasks':"SELECT json_agg(x) FROM (SELECT id,name,uuid,created_at,updated_at,status,progress,sn,drone_sn,bid,parent_task_id,begin_at,run_at,completed_at,wayline_uuid,prj_uuid,wayline_mission_state,current_waypoint_index,total_waypoints FROM tasks WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 8)x",
 'latest_flights':"SELECT json_agg(x) FROM (SELECT * FROM flight_tasks ORDER BY created_at DESC LIMIT 5)x",
 'latest_exceptions':"SELECT json_agg(x) FROM (SELECT * FROM exceptions ORDER BY created_at DESC LIMIT 12)x",
 'latest_pending':"SELECT json_agg(x) FROM (SELECT * FROM task_pending_exceptions ORDER BY created_at DESC LIMIT 8)x"
}
for key,sql in queries.items(): print(key,pg('es_task',sql))
print('TIME',pg('es_task','SELECT now()'))
