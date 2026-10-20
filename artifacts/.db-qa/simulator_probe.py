import subprocess,json,re,urllib.request,urllib.error
def run(args):
 try:return subprocess.run(args,text=True,capture_output=True,timeout=8).stdout
 except subprocess.TimeoutExpired:return '[TIMEOUT]'
def safe(v):
 if isinstance(v,dict):return {k:('[REDACTED]' if re.search('password|token|secret|key|credential|bind_code',str(k),re.I) else safe(x)) for k,x in v.items()}
 if isinstance(v,list):return [safe(x) for x in v]
 if isinstance(v,str):return re.sub(r'(://)[^/@\s]+@',r'\1[REDACTED]@',v)
 return v
out={}
for path in ['/deploy/config.yaml','/app/config/config.tmpl.yaml','/app/config/config.yaml']:
 text=run(['docker','exec','pf-device-simulator-service','cat',path])
 try:
  import yaml
  out[path]=safe(yaml.safe_load(text))
 except Exception:out[path]={'readable':bool(text),'keys':re.findall(r'^\s*([\w-]+):',text)}
for path in ['/health','/healthz','/simulator','/swagger/index.html']:
 try:
  r=urllib.request.urlopen('http://10.0.2.28:8080'+path,timeout=4)
  out[path]={'status':r.status,'body':r.read(1200).decode(errors='replace')}
 except urllib.error.HTTPError as e:out[path]={'status':e.code,'body':e.read(600).decode(errors='replace')}
 except Exception as e:out[path]={'error':str(e)}
cs=json.loads(run(['docker','inspect','traefik','frontend']))
out['entrypoints']=[{'container':c['Name'],'ports':c['NetworkSettings']['Ports'],'mounts':[{'source':m['Source'],'target':m['Destination']} for m in c['Mounts']]} for c in cs]
out['recent_log_file']=[]
for c in json.loads(run(['docker','inspect','pf-device-simulator-service'])):
 p=c.get('LogPath')
 if p:
  txt=run(['tail','-n','12',p])
  out['recent_log_file']=safe([json.loads(l).get('log','')[:700] for l in txt.splitlines() if l.startswith('{')])
print(json.dumps(out,ensure_ascii=False))
