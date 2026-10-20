import subprocess,json,re,pathlib
def run(args):
 try:
  p=subprocess.run(args,text=True,capture_output=True,timeout=12)
  return p.stdout[:18000]
 except subprocess.TimeoutExpired as e:return '[READ TIMEOUT] '+str(e.stdout or '')[:600]
def clean(s):
 s=re.sub(r'(?i)((?:password|passwd|token|secret|credential|access_key)[\w.-]*["\s:=]+)[^\s,;"}]+',r'\1[REDACTED]',s)
 return re.sub(r'(://)[^/@\s]+@',r'\1[REDACTED]@',s)
c=json.loads(run(['docker','inspect','pf-device-simulator-service']))[0]
out={'image':c['Config']['Image'],'status':c['State']['Status'],'started':c['State']['StartedAt'],'working_dir':c['Config']['WorkingDir'],'startup':clean(str(c['Config']['Entrypoint'])+' '+str(c['Config']['Cmd'])),'env_names':[e.split('=',1)[0] for e in c['Config']['Env']],'ports':c['NetworkSettings']['Ports'],'ip':[v['IPAddress'] for v in c['NetworkSettings']['Networks'].values()],'mounts':[{'source':m['Source'],'target':m['Destination']} for m in c['Mounts']],'labels':{k:clean(v) for k,v in c['Config']['Labels'].items() if 'traefik' in k}}
out['files']=clean(run(['docker','exec',c['Name'],'sh','-c',"ls -la /app; find /app -maxdepth 3 -type f \\( -name '*.yaml' -o -name '*.yml' -o -name '*.json' -o -name '*.sh' -o -name '*.md' -o -name '*.proto' \\) 2>/dev/null | head -60; printf '\\nLISTENERS\\n'; cat /proc/net/tcp /proc/net/tcp6"]))
out['log_tail']=clean(run(['docker','logs','--tail','35',c['Name']]))
out['config_files']=[]
for m in c['Mounts']:
 p=pathlib.Path(m['Source'])
 if p.is_file() and p.suffix in ('.yaml','.yml','.json'):
  try:
   import yaml
   data=yaml.safe_load(p.read_text())
   def safe(v):
    if isinstance(v,dict):return {k:('[REDACTED]' if re.search('password|token|secret|key|credential',str(k),re.I) else safe(x)) for k,x in v.items()}
    if isinstance(v,list):return [safe(x) for x in v]
    return clean(v) if isinstance(v,str) else v
   out['config_files'].append({'file':str(p),'config':safe(data)})
  except Exception:out['config_files'].append({'file':str(p),'available':True})
print(json.dumps(out,ensure_ascii=False))
