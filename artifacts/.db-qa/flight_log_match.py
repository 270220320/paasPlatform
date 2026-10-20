import subprocess,json,os
needles=['8d45c1c3-a9bc-46b1-839f-bff876e0c09b','d91335fa-9d42-4eef-a90a-1e2a863a2632','514146']
for name in ['es-task','pf-control-core']:
 c=json.loads(subprocess.check_output(['docker','inspect',name],text=True))[0]
 path=c['LogPath']
 with open(path,'rb') as f:
  f.seek(max(0,os.path.getsize(path)-24000000)); lines=f.read().decode(errors='replace').splitlines()
 matches=[x for x in lines if any(s in x for s in needles)]
 print(name,'matches',len(matches))
 matches=[x for x in matches if '514146' in x and ('2026-10-19T08:51:30' in x)]
 for x in matches[:12]:
  try:
   v=json.loads(x);print(v['time'],v['log'].strip()[:5000])
  except Exception:print(x[:3000])
