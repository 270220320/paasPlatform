#!/usr/bin/env bash
# 清理 verify-wayline-io.sh 产生的验证残留航线（id=27）
set -e
BASE=http://127.0.0.1:8080/api/v1
TOKEN=$(python3 - 1 <<'PYEOF'
import hmac, hashlib, base64, json, time, sys
secret = b'UAVPlatformDevSecretKeyMustBeLongEnoughForHS256Algorithm'
now = int(time.time())
head = {'alg': 'HS256', 'typ': 'JWT'}
payl = {'sub': sys.argv[1], 'workspaceId': 1, 'roleCode': 'super_admin',
        'permissions': ['wayline:read', 'wayline:write'], 'iat': now, 'exp': now + 600}
b64 = lambda d: base64.urlsafe_b64encode(d).rstrip(b'=')
seg = b64(json.dumps(head, separators=(',', ':')).encode()) + b'.' + b64(json.dumps(payl, separators=(',', ':')).encode())
sig = b64(hmac.new(secret, seg, hashlib.sha256).digest())
print((seg + b'.' + sig).decode())
PYEOF
)
curl -s -X DELETE "$BASE/waylines/27" -H "Authorization: Bearer $TOKEN"
echo
