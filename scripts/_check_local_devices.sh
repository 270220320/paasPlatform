#!/usr/bin/env bash
# 验证本地设备接口返回的 name
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
curl -sS -m 30 -H "Authorization: Bearer $TOKEN" \
  'http://127.0.0.1:8080/api/v1/devices?page=1&pageSize=100' | python3 -c "
import json,sys
d = json.load(sys.stdin)
for it in (d.get('data') or {}).get('list') or []:
    print(it.get('deviceType'), '|', repr(it.get('name')), '|', it.get('sn'), '|', it.get('onlineState'))
"
