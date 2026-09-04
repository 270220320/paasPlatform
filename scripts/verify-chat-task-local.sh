#!/usr/bin/env bash
# 验证对话创建的飞行任务本地可查且媒体已挂接：
# 1) GET /api/v1/missions?status=completed 列表含 chat-0826-151935
# 2) GET /api/v1/missions/{planId} 详情的 executions[].media 非空（司空 flightId 归组）
set -euo pipefail
BASE="http://127.0.0.1:8080"

LOGIN="$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin@aiflow.local","password":"123456"}')"
TOKEN="$(echo "$LOGIN" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')"
AUTH="Authorization: Bearer $TOKEN"
echo "== token ok =="

echo "== 1) 任务列表（找 chat-0826-151935）=="
curl -s -m 30 -G "$BASE/api/v1/missions" \
  --data-urlencode "page=1" --data-urlencode "size=10" \
  -H "$AUTH" | python3 -c '
import json, sys
d = json.load(sys.stdin)
items = d["data"]["list"] if isinstance(d.get("data"), dict) else d["data"]
hit = [x for x in items if "chat-" in (x.get("name") or "")]
print("total:", len(items))
for x in hit:
    print("HIT:", x["id"], x["name"], x.get("status"))
'

echo "== 2) plan 23 详情（executions + media）=="
curl -s -m 60 "$BASE/api/v1/missions/23" -H "$AUTH" | python3 -c '
import json, sys
d = json.load(sys.stdin)["data"]
print("plan:", d["plan"]["name"], d["plan"]["status"])
for e in d.get("executions", []):
    media = e.get("media") or []
    print("exec#{} status={} media_count={}".format(e["id"], e["status"], len(media)))
    for m in media[:5]:
        print("   -", m.get("name") or m.get("fileName"), "|", m.get("type") or m.get("mediaType"), "|", (m.get("previewUrl") or m.get("url") or "")[:80])
'
