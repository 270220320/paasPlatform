#!/usr/bin/env bash
# 触发司空设备同步（刷 t_device 的 name 昵称）并验证本地库
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
echo "--- sync ---"
curl -sS -m 60 -X POST -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:8080/api/v1/paas/devices/sync
echo
echo "--- local db ---"
'/mnt/c/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe' -uroot -pmeng1234 \
  -e "SELECT id,sn,name,device_type,online_state FROM UAVPlatform.v_device_list ORDER BY id" 2>/dev/null \
  || '/mnt/c/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe' -uroot -pmeng1234 \
  -e "SELECT d.id,d.sn,d.name,d.device_type,s.online_state FROM UAVPlatform.t_device d LEFT JOIN UAVPlatform.t_device_status s ON s.device_id=d.id WHERE d.deleted=0 ORDER BY d.id" 2>/dev/null
