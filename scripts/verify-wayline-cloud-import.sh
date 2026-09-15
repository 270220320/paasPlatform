#!/usr/bin/env bash
# 验证 KMZ 导入后同步上传司空并回写 uuid（可编辑）
set -e
BASE=http://127.0.0.1:8080/api/v1

TOKEN=$(python3 - <<'PYEOF'
import hmac, hashlib, base64, json, time
secret = b'UAVPlatformDevSecretKeyMustBeLongEnoughForHS256Algorithm'
now = int(time.time())
head = {'alg': 'HS256', 'typ': 'JWT'}
payl = {'sub': '1', 'workspaceId': 1, 'roleCode': 'super_admin',
        'permissions': ['wayline:read', 'wayline:write'], 'iat': now, 'exp': now + 3600}
b64 = lambda d: base64.urlsafe_b64encode(d).rstrip(b'=')
seg = b64(json.dumps(head, separators=(',', ':')).encode()) + b'.' + b64(json.dumps(payl, separators=(',', ':')).encode())
sig = b64(hmac.new(secret, seg, hashlib.sha256).digest())
print((seg + b'.' + sig).decode())
PYEOF
)

echo '--- 1. import the exported KMZ back ---'
curl -s -X POST "$BASE/waylines/import" -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/mirror-export.bin;type=application/vnd.google-earth.kmz" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('code=', d['code'], 'id=', d['data']['id'] if d.get('data') else None, 'name=', d['data']['name'] if d.get('data') else None)"

echo '--- 2. wait for cloud import event (uuid writeback) ---'
sleep 8

echo '--- 3. list waylines (check new record uuid) ---'
curl -s "$BASE/waylines?page=1&pageSize=20" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); [print(w['id'], w['name'], 'uuid=', w.get('fh2WaylineUuid')) for w in d['data']['list']]"

echo '--- 4. backend log: importKmz / event ---'
grep -E '\[wayline\] (importKmz|司空导入)' /mnt/d/AiFlow/backend/logs/spring-boot-run.log | tail -5
