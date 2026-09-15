#!/usr/bin/env bash
# 清理验证脏数据（33-36 镜像连带司空删除；29-32 纯本地删除）→ 重新导出 KMZ → 导入 → 验证轮询回写 uuid
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

echo '--- 1. cleanup dirty waylines (mirror 33-36 first: also deletes cloud) ---'
for id in 33 34 35 36 29 30 31 32; do
  R=$(curl -s -X DELETE "$BASE/waylines/$id" -H "Authorization: Bearer $TOKEN" | head -c 80)
  echo "delete $id -> $R"
done

echo '--- 2. list after cleanup ---'
curl -s "$BASE/waylines?page=1&pageSize=30" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); [print(w['id'], w['name'], 'uuid=', w.get('fh2WaylineUuid')) for w in d['data']['list']]"

echo '--- 3. export id=25 as KMZ ---'
curl -s -D /tmp/exp-h.txt -o /tmp/verify.kmz "$BASE/waylines/25/export" -H "Authorization: Bearer $TOKEN"
grep -i 'content-type' /tmp/exp-h.txt
echo "kmz size: $(stat -c %s /tmp/verify.kmz)"

echo '--- 4. re-import the KMZ (renamed 简单航线.kmz so name fallback works) ---'
cp /tmp/verify.kmz "/tmp/简单航线.kmz"
curl -s -X POST "$BASE/waylines/import" -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/简单航线.kmz;type=application/vnd.google-earth.kmz" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('code=', d['code'], 'id=', d['data']['id'] if d.get('data') else None, 'name=', d['data']['name'] if d.get('data') else None)"

echo '--- 5. wait 25s for poll-resolver writeback ---'
sleep 25

echo '--- 6. final list (expect new record with uuid => editable) ---'
curl -s "$BASE/waylines?page=1&pageSize=30" -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); [print(w['id'], w['name'], w.get('source'), 'uuid=', w.get('fh2WaylineUuid')) for w in d['data']['list']]"

echo '--- 7. resolver log ---'
grep -E '\[wayline\] (司空导入完成|importKmz 已提交|司空 uuid 轮询|司空航线重命名)' /mnt/d/AiFlow/backend/logs/spring-boot-run.log | tail -6
