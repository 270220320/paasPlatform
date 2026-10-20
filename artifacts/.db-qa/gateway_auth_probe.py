import subprocess,json,yaml,urllib.request,urllib.error
def run(a):return subprocess.check_output(a,text=True,timeout=15)
c=json.loads(run(['docker','inspect','pf-gateway-service']))[0]
print('PROCESS',c['Config']['Entrypoint'],c['Config']['Cmd'])
for path in ['/deploy/config.yaml','/app/config/config.yaml','/etc/nginx/nginx.conf']:
 p=subprocess.run(['docker','exec','pf-gateway-service','cat',path],capture_output=True,text=True,timeout=10)
 if p.returncode:continue
 try:
  v=yaml.safe_load(p.stdout)
  def shape(x):
   if isinstance(x,dict):return {str(k):shape(z) for k,z in x.items()}
   if isinstance(x,list):return [shape(z) for z in x[:8]]
   return type(x).__name__
  print('CONFIG_SHAPE',path,json.dumps(shape(v)))
 except Exception:pass
