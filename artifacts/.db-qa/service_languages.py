import subprocess,json,concurrent.futures
def run(args,timeout=12):
 try:
  p=subprocess.run(args,text=True,capture_output=True,timeout=timeout)
  if p.returncode:return 'ERROR: '+p.stderr.strip()[:200]
  return p.stdout.strip()
 except subprocess.TimeoutExpired:return ''
cs=json.loads(run(['docker','inspect']+run(['docker','ps','-q']).split(),25))
def inspect(c):
 name=c['Name'].lstrip('/')
 procs=run(['docker','top',name,'-eo','pid,comm'])
 cfg=c['Config']; startup=' '.join((cfg.get('Entrypoint') or [])+(cfg.get('Cmd') or []))
 # Report only executable/process names and package paths, never command-line secrets.
 item={'container':name,'image':cfg['Image'],'processes':procs.splitlines()[1:],'error':procs if procs.startswith('ERROR:') else ''}
 if any('java' in s.lower() for s in procs.splitlines()[1:]) or '.jar' in startup or 'java ' in startup:
  item['language']='Java'
  item['jars']=run(['docker','exec',name,'sh','-c',"find /app /apps /opt /srv /home /usr/local -maxdepth 5 -type f \\( -name '*.jar' -o -name '*.war' \\) 2>/dev/null | head -12"],15).splitlines()
 else:
  item['language']='unclassified'
 return item
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:out=list(pool.map(inspect,cs))
print(json.dumps(out,ensure_ascii=False))
