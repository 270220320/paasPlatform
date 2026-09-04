#!/usr/bin/env bash
# 媒体诊断（五）：curl 签名URL下载 kmz + python zipfile 解析拍照动作 + module 分布统计
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S -v 2>/dev/null
cat > /tmp/getkmz.py <<'PYEOF'
import re, subprocess, zipfile, collections
logs = subprocess.run(['sudo','docker','logs','traefik','--since','100m'], capture_output=True, text=True).stdout
lines = [l for l in logs.splitlines() if 'flighttask_resource_get' in l]
last = lines[-1] if lines else ''
m = re.search(r'before=\{.*?"url":"(.*?)",', last)
url = m.group(1).replace('\\u0026','&') if m else ''
print('URL_HEAD:', url[:100])
r = subprocess.run(['curl','-s','-o','/tmp/task.kmz','-w','%{http_code} %{size_download}',url], capture_output=True, text=True).stdout
print('CURL:', r)
try:
    z = zipfile.ZipFile('/tmp/task.kmz')
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
except Exception as e:
    print('KMZ_PARSE_ERR:', e)
PYEOF
python3 /tmp/getkmz.py
echo ===MODULE_DIST_2H===;
sudo docker logs tas-service --since 120m 2>&1 | grep -ao '"method":"file_upload_callback".*"module":[0-9]*' | grep -ao '"module":[0-9]*' | sort | uniq -c;
echo ===CALLBACK_FLIGHTIDS_COUNT===;
sudo docker logs tas-service --since 120m 2>&1 | grep -ao '"flight_id":"[0-9a-f-]*"' | sort -u | wc -l;
echo ===DONE===
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
