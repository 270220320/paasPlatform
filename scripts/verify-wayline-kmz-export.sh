#!/usr/bin/env bash
# 验证司空镜像导出 KMZ（getWaylineDetail → URL → 下载）
set -e
BASE=http://127.0.0.1:8080/api/v1

USER_ID=$(mysql -h 172.28.208.1 -uroot -pmeng1234 -N -e "select 1" 2>/dev/null || echo 1)
USER_ID=${USER_ID:-1}
TOKEN=$(python3 - "$USER_ID" <<'PYEOF'
import hmac, hashlib, base64, json, time, sys
secret = b'UAVPlatformDevSecretKeyMustBeLongEnoughForHS256Algorithm'
now = int(time.time())
head = {'alg': 'HS256', 'typ': 'JWT'}
payl = {'sub': sys.argv[1], 'workspaceId': 1, 'roleCode': 'super_admin',
        'permissions': ['wayline:read', 'wayline:write'], 'iat': now, 'exp': now + 3600}
b64 = lambda d: base64.urlsafe_b64encode(d).rstrip(b'=')
seg = b64(json.dumps(head, separators=(',', ':')).encode()) + b'.' + b64(json.dumps(payl, separators=(',', ':')).encode())
sig = b64(hmac.new(secret, seg, hashlib.sha256).digest())
print((seg + b'.' + sig).decode())
PYEOF
)

echo '--- 1. list waylines ---'
curl -s "$BASE/waylines?page=1&pageSize=20" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); [print(w['id'], w['name'], w.get('fh2WaylineUuid')) for w in d['data']['list']]"

FH_ID=$(curl -s "$BASE/waylines?page=1&pageSize=50" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print(next((w['id'] for w in d['data']['list'] if w.get('fh2WaylineUuid')), ''))")
if [ -z "$FH_ID" ]; then echo '!! 无镜像航线'; exit 0; fi

echo "--- 2. export mirror wayline id=$FH_ID (expect .kmz) ---"
curl -s -D /tmp/export-h.txt -o /tmp/mirror-export.bin "$BASE/waylines/$FH_ID/export" -H "Authorization: Bearer $TOKEN"
grep -iE 'content-type|content-disposition' /tmp/export-h.txt
echo "size: $(stat -c %s /tmp/mirror-export.bin) bytes"
echo "magic: $(head -c 2 /tmp/mirror-export.bin | od -An -tx1 | tr -d ' ')"
