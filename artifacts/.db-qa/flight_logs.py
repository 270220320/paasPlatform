import subprocess,json,urllib.request,http.cookiejar,yaml
def run(a):
 p=subprocess.run(a,text=True,capture_output=True,timeout=18)
 if p.returncode: return 'ERROR '+p.stderr[:250]
 return p.stdout
for name in ['es-task','pf-device-simulator-service','pf-control-core']:
 c=json.loads(run(['docker','inspect',name]))[0]
 print('LOG_CONFIG',json.dumps({'name':name,'driver':c['HostConfig']['LogConfig'],'path':c['LogPath'],'mounts':c['Mounts']},ensure_ascii=False))
config=yaml.safe_load(run(['docker','exec','pf-device-simulator-service','cat','/deploy/config.yaml']))
jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
def req(path,data=None):
 q=urllib.request.Request('http://192.168.76.129:30812/simulator/api/v1'+path,data=json.dumps(data).encode() if data else None,headers={'Content-Type':'application/json'} if data else {})
 return json.loads(opener.open(q,timeout=8).read(200000))
a=config['auth'];req('/auth/login',{'username':a['default_username'],'password':a['default_password']})
d=req('/devices')
def redact(v):
 if isinstance(v,dict):return {k:('[REDACTED]' if any(s in k.lower() for s in ['password','secret','token','nonce']) else redact(x)) for k,x in v.items()}
 if isinstance(v,list):return [redact(x) for x in v]
 return v
print('SIM_DEVICES',json.dumps(redact(d),ensure_ascii=False))
for sn in ['8UUXN2K00A0144','1581F8HGX252500A009X']:
 try: print('SIM_LOGS',sn,json.dumps(redact(req('/devices/'+sn+'/logs')),ensure_ascii=False))
 except Exception as e: print('SIM_LOGS_ERROR',sn,str(e))
