#!/usr/bin/env bash
# 验证司空镜像航线导出 JSON 回导后 fh2WaylineUuid 恢复（导入即可编辑）
set -e
BASE=http://127.0.0.1:8080/api/v1

USER_ID=$(mysql -h 172.28.208.1 -uroot -pmeng1234 -N -e "select id from UAVPlatform.t_user where account='admin@aiflow.local' limit 1" 2>/dev/null || true)
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

echo '--- 1. list waylines (id / name / fh2WaylineUuid) ---'
curl -s "$BASE/waylines?page=1&pageSize=20" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); [print(w['id'], w['name'], w.get('fh2WaylineUuid')) for w in d['data']['list']]"

# 取第一条带 fh2WaylineUuid 的司空镜像航线
FH_ID=$(curl -s "$BASE/waylines?page=1&pageSize=50" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print(next((w['id'] for w in d['data']['list'] if w.get('fh2WaylineUuid')), ''))")
if [ -z "$FH_ID" ]; then echo '!! 没有带 fh2WaylineUuid 的镜像航线，跳过闭环验证'; exit 0; fi
echo "FH_MIRROR_ID=$FH_ID"

echo '--- 2. export mirror wayline (should be JSON with fh2WaylineUuid) ---'
curl -s -o /tmp/mirror-export.json "$BASE/waylines/$FH_ID/export" -H "Authorization: Bearer $TOKEN"
python3 -c "import json; d=json.load(open('/tmp/mirror-export.json')); print('format=', d['format'], 'uuid=', d['wayline'].get('fh2WaylineUuid'), 'name=', d['wayline']['name'])"

echo '--- 3. 原记录保留（WSL 无 mysql client，改为验证同 uuid upsert 恢复分支）---'

echo '--- 4. re-import export JSON (should restore with uuid) ---'
curl -s -X POST "$BASE/waylines/import" -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/mirror-export.json;type=application/json" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('code=', d['code'], 'restoredId=', d['data']['id'] if d.get('data') else None, 'name=', d['data']['name'] if d.get('data') else None)"

echo '--- 5. verify restored wayline carries fh2WaylineUuid (editable in editor) ---'
curl -s "$BASE/waylines?page=1&pageSize=50" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); [print(w['id'], w['name'], 'uuid=', w.get('fh2WaylineUuid')) for w in d['data']['list'] if w.get('fh2WaylineUuid')]"
