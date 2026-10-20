import subprocess,json,urllib.request,urllib.error,http.cookiejar,re
import yaml
config=yaml.safe_load(subprocess.check_output(['docker','exec','pf-device-simulator-service','cat','/deploy/config.yaml'],text=True))
jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
base='http://192.168.76.129:30812'
def request(path,data=None):
 req=urllib.request.Request(base+path,data=json.dumps(data).encode() if data is not None else None,headers={'Content-Type':'application/json'} if data is not None else {})
 try:
  r=opener.open(req,timeout=8);raw=r.read(200000).decode(errors='replace')
  return r.status,raw
 except urllib.error.HTTPError as e:return e.code,e.read(1000).decode(errors='replace')
auth=config['auth']
status,raw=request('/simulator/api/v1/auth/login',{'username':auth['default_username'],'password':auth['default_password']})
out={'login_status':status,'session_received':any(c.name=='sim_session' for c in jar),'reads':[]}
if status==200:
 for path in ['/simulator/api/v1/auth/me','/simulator/api/v1/devices','/simulator/api/v1/defaults','/simulator/admin/']:
  code,text=request(path)
  item={'path':path,'status':code}
  try:
   data=json.loads(text)
   if 'devices' in path:
    item['result']=data
   elif 'defaults' in path:item['result']={k:('[REDACTED]' if re.search('password|secret|token',k,re.I) else v) for k,v in data.items()}
   else:item['keys']=list(data) if isinstance(data,dict) else type(data).__name__
  except Exception:item['page_title']=re.findall('<title>(.*?)</title>',text);item['assets']=re.findall(r'(?:src|href)="([^"]+)"',text)[:12]
  out['reads'].append(item)
print(json.dumps(out,ensure_ascii=False))
