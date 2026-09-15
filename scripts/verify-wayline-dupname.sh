#!/usr/bin/env bash
# 同名多航线场景验证：导出 id=25 → 再导入 → 司空新增第三条同名航线 → 轮询必须回写未占用的新 uuid
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
print((seg + b'.' + b64(hmac.new(secret, seg, hashlib.sha256).digest())).decode())
PYEOF
)
echo '--- 1. wait ready ---'
for i in $(seq 1 60); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/waylines?page=1&pageSize=1" -H "Authorization: Bearer $TOKEN")
  if [ "$CODE" = "200" ]; then echo "ready ~$((i*2))s"; break; fi
  sleep 2
done
if [ "$CODE" != "200" ]; then echo 'NOT ready'; exit 1; fi

echo '--- 2. baseline list (expect 25=8a4fe6dd..., 38=e12826e7...) ---'
curl -s "$BASE/waylines?page=1&pageSize=30" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); [print(w['id'], repr(w['name']), 'uuid=', w.get('fh2WaylineUuid')) for w in d['data']['list']]"

echo '--- 3. export id=25 KMZ and re-import ---'
curl -s -o /tmp/dup.kmz "$BASE/waylines/25/export" -H "Authorization: Bearer $TOKEN"
echo "kmz size: $(stat -c %s /tmp/dup.kmz)"
cp /tmp/dup.kmz "/tmp/简单航线.kmz"
NEW_ID=$(curl -s -X POST "$BASE/waylines/import" -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/简单航线.kmz;type=application/vnd.google-earth.kmz" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['data']['id'] if d.get('data') else 'FAIL')")
echo "new wayline id: $NEW_ID"

echo '--- 4. wait 25s for poll writeback ---'
sleep 25

echo '--- 5. final list: new record uuid must be NEW (not 8a4fe6dd/e12826e7) ---'
curl -s "$BASE/waylines?page=1&pageSize=30" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "
import json,sys
d=json.load(sys.stdin)
OLD={'8a4fe6dd-825d-4546-91bd-08589e0770ac','e12826e7-6dab-45e1-b74f-b9edd5ce9873'}
newid=$NEW_ID
ok=False
for w in d['data']['list']:
    mark=''
    if w['id']==newid:
        u=w.get('fh2WaylineUuid') or ''
        ok=bool(u) and u not in OLD
        mark=' <== NEW, uuid is fresh' if ok else ' <== BAD: uuid empty or collides old'
    print(w['id'], repr(w['name']), 'uuid=', w.get('fh2WaylineUuid'), mark)
print('RESULT:', 'PASS' if ok else 'FAIL')
"

echo '--- 6. resolver log ---'
grep -E '司空导入完成|importKmz 已提交' /mnt/d/AiFlow/backend/logs/spring-boot-run.log | tail -3

echo '--- 7. cleanup new record (also deletes its cloud copy) ---'
curl -s -X DELETE "$BASE/waylines/$NEW_ID" -H "Authorization: Bearer $TOKEN" | head -c 80; echo
