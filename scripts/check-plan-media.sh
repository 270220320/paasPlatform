#!/usr/bin/env bash
# 验证媒体挂接链路：plan 20（司空侧有 4 条媒体的 8-25 任务）详情 media_count 应为 4
set -euo pipefail
BASE="http://127.0.0.1:8080"
LOGIN="$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin@aiflow.local","password":"123456"}')"
TOKEN="$(echo "$LOGIN" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')"

for PID in "$@"; do
  echo "== plan $PID detail =="
  curl -s -m 60 "$BASE/api/v1/missions/$PID" -H "Authorization: Bearer $TOKEN" | python3 -c '
import json, sys
d = json.load(sys.stdin)["data"]
print("plan:", d["plan"]["id"], d["plan"]["status"])
for e in d.get("executions", []):
    media = e.get("media") or []
    print("exec {} uuid={} status={} media_count={}".format(
        e["id"], (e.get("fh2TaskUuid") or "")[:8], e["status"], len(media)))
    for m in media[:4]:
        print("   -", m.get("name") or m.get("fileName"), "|", m.get("type") or m.get("mediaType"))
'
done
