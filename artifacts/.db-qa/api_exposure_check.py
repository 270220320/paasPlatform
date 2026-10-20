import subprocess,json,urllib.request,urllib.error,re
def run(a):
 p=subprocess.run(a,text=True,capture_output=True,timeout=20)
 if p.returncode: raise RuntimeError(p.stderr[:200])
 return p.stdout
rows=[json.loads(x) for x in run(['docker','ps','--format','{{json .}}']).splitlines()]
for x in rows:
 if any(w in x['Names'] for w in ['gateway','nginx','openapi']):print('ENTRY',x['Names'],x['Ports'])
base='http://192.168.76.129:30812'
for path in ['/gateway/atc/api/v1/flight-tasks/8d45c1c3-a9bc-46b1-839f-bff876e0c09b','/atc/api/v1/flight-tasks/8d45c1c3-a9bc-46b1-839f-bff876e0c09b']:
 headers={'auth_keys':'user_id','user_id':'paas_test','X-Project-Uuid':'5a6f9d4b-2a38-4b4b-86f9-3a678da0bf88'}
 try:r=urllib.request.urlopen(urllib.request.Request(base+path,headers=headers),timeout=8)
 except urllib.error.HTTPError as e:r=e
 raw=r.read(2000).decode(errors='replace');out={'path':path,'http_status':r.status,'type':r.headers.get('Content-Type')}
 try:
  d=json.loads(raw);out['keys']=list(d);out.update({k:d[k] for k in ['code','message','msg'] if k in d})
  if isinstance(d.get('data'),dict):out['task']={k:d['data'][k] for k in ['uuid','name','status'] if k in d['data']}
 except Exception:out['text']=re.sub('<[^>]*>',' ',raw)[:160]
 print('READ_ONLY_PROBE',json.dumps(out,ensure_ascii=False))
