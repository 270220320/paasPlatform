import subprocess,json,re
def run(args):
 p=subprocess.run(args,text=True,capture_output=True,timeout=35)
 return {'code':p.returncode,'stdout':p.stdout[:24000],'stderr':p.stderr[:400]}
c=json.loads(subprocess.check_output(['docker','inspect','es-flysafe']))[0]
def clean(s):
 s=re.sub(r'(?i)((?:password|passwd|token|secret|credential)[^=\s]*[=\s]+)\S+',r'\1[REDACTED]',s)
 return re.sub(r'(://)[^/@\s]+@',r'\1[REDACTED]@',s)
out={'image':c['Config']['Image'],'working_dir':c['Config']['WorkingDir'],'entrypoint':clean(str(c['Config']['Entrypoint'])),'cmd':clean(str(c['Config']['Cmd'])),'mounts':[{'source':m['Source'],'target':m['Destination']} for m in c['Mounts']],'processes':run(['docker','top','es-flysafe','-eo','pid,comm'])}
out['files']=run(['docker','exec','es-flysafe','sh','-c',"printf 'PROCESS\n'; ls -l /proc/1/exe /proc/1/cwd; printf '\nAPPLICATION ROOTS\n'; ls -la / /app /apps /opt /srv /usr/local/bin 2>/dev/null; printf '\nPACKAGES\n'; find / -xdev -type f \\( -iname '*.jar' -o -iname '*.war' -o -iname '*.ear' -o -iname '*.class' -o -iname '*.py' -o -iname '*.pyc' -o -name 'go.mod' -o -name 'package.json' \\) 2>/dev/null | head -100"])
print(json.dumps(out,ensure_ascii=False))
