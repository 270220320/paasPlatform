#!/usr/bin/env bash
# 媒体诊断（五c）：urllib 下载 kmz 并解析拍照动作
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
cat > /tmp/getkmz2.py <<'PYEOF'
import re, subprocess, zipfile, collections, urllib.request
p = subprocess.run("echo Tksc@197653 | sudo -S docker logs traefik --since 100m 2>&1", shell=True, capture_output=True, text=True)
lines = [l for l in (p.stdout + p.stderr).splitlines() if 'flighttask_resource_get' in l]
last = lines[-1] if lines else ''
m = re.search(r'"url":"([^"]*)"', last)
url = m.group(1).replace('\\u0026','&') if m else ''
try:
    req = urllib.request.urlopen(url, timeout=10)
    data = req.read()
    print('HTTP:', req.status, 'SIZE:', len(data))
except Exception as e:
    print('DL_ERR:', e)
    raise SystemExit
import io
z = zipfile.ZipFile(io.BytesIO(data))
print('FILES:', z.namelist())
kml = z.read('wpmz/template.kml').decode('utf-8', 'ignore')
print('Placemarks:', len(re.findall(r'<Placemark>', kml)))
acts = collections.Counter(re.findall(r'<wpml:actionActuatorFunc>([^<]*)</wpml:actionActuatorFunc>', kml))
print('ACTIONS:', dict(acts))
ats = collections.Counter(re.findall(r'actionType="([^"]*)"', kml))
print('ACTION_TYPES:', dict(ats))
for tag in ['globalHeight','executeHeight','autoFlightSpeed','waylineType','templateType','templateSubType']:
    mm = re.search(r'<wpml:%s>([^<]*)</wpml:%s>' % (tag, tag), kml) or re.search(r'<wpml:%s type="([^"]*)"' % tag, kml)
    print(tag.upper()+':', mm.group(1) if mm else '-')
PYEOF
python3 /tmp/getkmz2.py
echo ===DONE===
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
