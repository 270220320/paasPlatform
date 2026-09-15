#!/usr/bin/env bash
# 验证司空远端设备列表返回的 name 字段（手工签 dev JWT）
set -e
TOKEN=$(python3 - <<'PY'
import hmac, hashlib, base64, json, time
def b64(b): return base64.urlsafe_b64encode(b).rstrip(b'=').decode()
secret = b'UAVPlatformDevSecretKeyMustBeLongEnoughForHS256Algorithm'
now = int(time.time())
h = b64(json.dumps({"alg":"HS256","typ":"JWT"}, separators=(',',':')).encode())
p = b64(json.dumps({
    "sub":"1","workspaceId":1,"roleCode":"super_admin",
    "permissions":["device:read","device:write"],
    "iat":now,"exp":now+600
}, separators=(',',':')).encode())
sig = b64(hmac.new(secret, (h+'.'+p).encode(), hashlib.sha256).digest())
print(h+'.'+p+'.'+sig)
PY
)
curl -sS -m 60 -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:8080/api/v1/paas/devices/remote | python3 -m json.tool
