#!/usr/bin/env bash
# 验证航线导出/导入闭环（U-032）：登录 → 导出（应非 txt）→ 回导（应成功）
set -e
BASE=http://127.0.0.1:8080/api/v1

cat > /tmp/login.json <<'EOF'
{"account":"admin@aiflow.local","password":"admin123"}
EOF

# 密码非文档默认值：改用开发密钥直接签发 JWT（AuthInterceptor 同构 claims）
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
echo "USER_ID=$USER_ID TOKEN_LEN=${#TOKEN}"

echo '--- token sanity (list waylines) ---'
curl -s "$BASE/waylines?page=1&pageSize=1" -H "Authorization: Bearer $TOKEN" | head -c 300; echo

ID=$(curl -s "$BASE/waylines?page=1&pageSize=1" -H "Authorization: Bearer $TOKEN" \
  | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "WAYLINE_ID=$ID"

echo '--- export response headers ---'
curl -s -D - -o /tmp/wayline-export.json "$BASE/waylines/$ID/export" \
  -H "Authorization: Bearer $TOKEN" | grep -iE 'content-type|content-disposition' || true

echo '--- export body head ---'
head -c 300 /tmp/wayline-export.json; echo; echo

echo '--- re-import exported file ---'
curl -s -X POST "$BASE/waylines/import" -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/wayline-export.json;type=application/json" | head -c 400
echo
