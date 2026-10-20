import subprocess,json,re,datetime,pathlib

def run(args):
    p=subprocess.run(args,text=True,capture_output=True,timeout=45)
    if p.returncode: raise RuntimeError(p.stderr[:300])
    return p.stdout

containers=json.loads(run(['docker','inspect']+run(['docker','ps','-q']).split()))
by={c['Name'].lstrip('/'):c for c in containers}
def env(c):return dict(x.split('=',1) for x in c['Config']['Env'] if '=' in x)
out={'host':'192.168.76.129','collected':datetime.datetime.now().isoformat(),'instances':{},'references':[]}
for name in ['mysql_instance_1','postgres_instance_1','postgres_instance_2','timescaledb']:
    c=by[name]; e=env(c)
    item={'image':c['Config']['Image'],'mounts':[{'source':m['Source'],'target':m['Destination']} for m in c['Mounts']],'databases':{}}
    out['instances'][name]=item
    if name.startswith('mysql'):
        def query(db,sql):
            return run(['docker','exec',name,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B '+(' --database="'+db+'"' if db else '')+' -e '+"'"+sql.replace("'","'\"'\"'")+"'"])
        item['version']=query('', 'SELECT VERSION()').strip()
        item['clients']=query('',"SELECT IFNULL(DB,''),SUBSTRING_INDEX(HOST,':',1),COUNT(*) FROM information_schema.PROCESSLIST WHERE HOST NOT IN ('localhost','') GROUP BY DB,SUBSTRING_INDEX(HOST,':',1)").splitlines()
        dbs=query('','SHOW DATABASES').splitlines()
        for db in dbs:
            tables=query(db,"SELECT table_name,table_comment FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name")
            item['databases'][db]={'tables':[{'name':r.split('\t')[0],'comment':'\t'.join(r.split('\t')[1:])} for r in tables.splitlines()]}
    else:
        def query(db,sql):
            return run(['docker','exec',name,'sh','-c','PGPASSWORD="$POSTGRES_PASSWORD" exec psql -X -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d '+db+' -At -c '+"'"+sql.replace("'","'\"'\"'")+"'"])
        item['version']=query('postgres','SELECT version()').strip()
        item['clients']=query('postgres',"SELECT coalesce(datname,'')||'|'||coalesce(client_addr::text,'')||'|'||count(*) FROM pg_stat_activity WHERE client_addr IS NOT NULL GROUP BY datname,client_addr ORDER BY datname").splitlines()
        item['templates']=query('postgres',"SELECT datname FROM pg_database WHERE datistemplate ORDER BY datname").splitlines()
        dbs=query('postgres',"SELECT datname FROM pg_database WHERE NOT datistemplate OR datname='template_postgis' ORDER BY datname").splitlines()
        for db in dbs:
            sql="SELECT coalesce(json_agg(x),'[]'::json) FROM (SELECT schemaname AS schema,tablename AS name FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema') ORDER BY schemaname,tablename)x"
            tables=json.loads(query(db,sql))
            exts=query(db,"SELECT extname||':'||extversion FROM pg_extension ORDER BY extname").splitlines()
            d={'tables':tables,'extensions':exts}
            if any(x.startswith('timescaledb:') for x in exts):
                d['hypertables']=query(db,"SELECT hypertable_schema||'.'||hypertable_name FROM timescaledb_information.hypertables ORDER BY 1").splitlines()
            item['databases'][db]=d
            if name=='timescaledb':
                d['columns']=query(db,"SELECT table_name||'.'||column_name||':'||data_type FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position").splitlines()
for name,c in by.items():
    refs=[]
    for k,v in env(c).items():
        if re.search(r'password|passwd|secret|token|credential|key',k,re.I):continue
        if re.search(r'jdbc:|postgres(?:ql)?://|mysql://',v,re.I):
            safe=re.sub(r'//[^/@\s]+@','//',v).split('?',1)[0]
            refs.append({'key':k,'endpoint':safe})
        elif re.search(r'(?:database|datasource|postgres|mysql|timescale|db_).*?(?:host|port|name)$',k,re.I):
            if len(v)<150:refs.append({'key':k,'endpoint':v})
    if refs:out['references'].append({'container':name,'refs':refs})
def endpoints(value):
    return sorted(set(re.findall(r'(?:mysql_instance_1|postgres_instance_[12]|timescaledb|10\.0\.2\.(?:8|13|14|15))(?::\d+)?(?:/[A-Za-z0-9_]+)?',value)))
out['config_references']=[]
root=pathlib.Path('/opt/cloudApi2_3.1/install')
for p in root.rglob('*'):
    if p.is_file() and p.suffix in ('.yaml','.yml','.properties','.conf','.json','.toml') and p.stat().st_size<1500000:
        try:s=p.read_text(errors='replace')
        except OSError:continue
        ep=endpoints(s)
        if ep:out['config_references'].append({'file':str(p),'endpoints':ep})
sql="SELECT n.AppId,n.NamespaceName,i.`Key`,i.Value FROM Item i JOIN Namespace n ON i.NamespaceId=n.Id WHERE i.IsDeleted=0 AND n.IsDeleted=0"
apollo=run(['docker','exec','mysql_instance_1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B --default-character-set=utf8mb4 ApolloConfigDB -e '+"'"+sql+"'"])
out['apollo_references']=[]
for row in apollo.splitlines():
    vals=row.split('\t',3)
    if len(vals)!=4:continue
    app,ns,key,value=vals
    if re.search('password|secret|token|credential',key,re.I):continue
    ep=endpoints(value)
    if ep:out['apollo_references'].append({'app':app,'namespace':ns,'key':key,'endpoints':ep})
out['container_ips']={n:[v['IPAddress'] for v in c['NetworkSettings']['Networks'].values()] for n,c in by.items()}
print(json.dumps(out,ensure_ascii=False))
