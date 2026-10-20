import subprocess,json,shlex
def run(args):
 p=subprocess.run(args,text=True,capture_output=True,timeout=30)
 if p.returncode:raise RuntimeError(p.stderr[:250])
 return p.stdout.strip()
def my(sql):
 return run(['docker','exec','mysql_instance_1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B -e '+shlex.quote(sql)])
result={'mysql':{'server':my('SELECT @@global.read_only,@@global.super_read_only'),'accounts':[]}}
for host in my("SELECT Host FROM mysql.user WHERE User='root'").splitlines():
 result['mysql']['accounts'].append({'user':'root','host':host,'grants':my("SHOW GRANTS FOR 'root'@'"+host.replace("'","''")+"'")})
for name,user in [('postgres_instance_1','app_rw'),('postgres_instance_2','app_rw'),('timescaledb','postgres')]:
 def pg(db,sql):
  return run(['docker','exec',name,'sh','-c','PGPASSWORD="$POSTGRES_PASSWORD" exec psql -X -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d '+shlex.quote(db)+' -At -c '+shlex.quote(sql)])
 role=json.loads(pg('postgres',"SELECT row_to_json(t) FROM (SELECT rolname,rolsuper,rolinherit,rolcreaterole,rolcreatedb,rolcanlogin,rolbypassrls,rolconfig FROM pg_roles WHERE rolname='"+user+"')t"))
 item={'role':role,'read_only':pg('postgres',"SELECT current_setting('default_transaction_read_only')||'|'||current_setting('transaction_read_only')||'|'||pg_is_in_recovery()::text"),'database_settings':pg('postgres',"SELECT coalesce(d.datname,'ALL')||'|'||coalesce(r.rolname,'ALL')||'|'||array_to_string(s.setconfig,',') FROM pg_db_role_setting s LEFT JOIN pg_database d ON d.oid=s.setdatabase LEFT JOIN pg_roles r ON r.oid=s.setrole")}
 if not role['rolsuper']:
  item['table_permissions']={}
  for db in pg('postgres',"SELECT datname FROM pg_database WHERE NOT datistemplate AND datallowconn").splitlines():
   item['table_permissions'][db]=pg(db,"SELECT count(*),count(*) FILTER(WHERE has_table_privilege('"+user+"',c.oid,'SELECT')),count(*) FILTER(WHERE has_table_privilege('"+user+"',c.oid,'INSERT')),count(*) FILTER(WHERE has_table_privilege('"+user+"',c.oid,'UPDATE')),count(*) FILTER(WHERE has_table_privilege('"+user+"',c.oid,'DELETE')) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p')")
 result[name]=item
print(json.dumps(result))
