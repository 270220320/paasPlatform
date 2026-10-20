import subprocess,json,re,tarfile,io,urllib.request,urllib.error
def run(args,timeout=20):
 try:return subprocess.run(args,text=True,capture_output=True,timeout=timeout).stdout
 except subprocess.TimeoutExpired:return '[TIMEOUT]'
out={}
for path in ['/simulator','/simulator/']:
 try:
  r=urllib.request.urlopen('http://192.168.76.129:30812'+path,timeout=4);out[path]={'status':r.status,'body':r.read(200).decode(errors='replace')}
 except urllib.error.HTTPError as e:out[path]={'status':e.code,'body':e.read(200).decode(errors='replace')}
 except Exception as e:out[path]={'error':str(e)}
out['frontend_dirs']=run(['docker','exec','frontend','sh','-c',"find /app -maxdepth 3 -type d 2>/dev/null | head -70"])
data=subprocess.check_output(['docker','cp','pf-device-simulator-service:/app/server','-'],timeout=25)
t=tarfile.open(fileobj=io.BytesIO(data));binary=t.extractfile(t.getmembers()[0]).read()
out['route_literals']=sorted(set(x.decode(errors='replace') for x in re.findall(rb'/simulator/[A-Za-z0-9_{}:/.-]{1,140}',binary)))[:65]
out['auth_symbols']=sorted(set(x.decode(errors='replace') for x in re.findall(rb'[A-Za-z0-9_./*-]{0,110}(?:Login|AuthService|ListDevices|CreateDevice)[A-Za-z0-9_./*-]{0,80}',binary)))[:40]
print(json.dumps(out,ensure_ascii=False))
