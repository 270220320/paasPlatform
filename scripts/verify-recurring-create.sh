#!/usr/bin/env bash
# 端到端验证周期任务修复：创建应成功并降级本地调度（不再报 50008 需配置 route-api-base/userToken）
set -uo pipefail
BASE="http://127.0.0.1:8080"

LOGIN="$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin@aiflow.local","password":"123456"}')"
TOKEN="$(echo "$LOGIN" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')"
[ -n "$TOKEN" ] || { echo "LOGIN FAILED"; exit 1; }
echo "== 1) login ok =="

# 今天起 3 天、每天 23:50 窗口（远离当前时间，创建后立即取消，不会真实起飞）
D1="$(date +%F)"; D2="$(date -d '+2 days' +%F)"
echo "== 2) create recurring mission (dateStart=$D1 dateEnd=$D2 win=23:50) =="
RESP="$(curl -s -m 15 -X POST "$BASE/api/v1/missions" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{
    \"name\":\"R-verify-周期降级验证\",
    \"taskType\":\"normal\",
    \"waylineId\":23,
    \"deviceId\":1,
    \"strategy\":\"recurring\",
    \"dateStart\":\"$D1\",
    \"dateEnd\":\"$D2\",
    \"timeWindows\":[{\"start\":\"23:50\",\"end\":\"23:59\"}],
    \"freqInterval\":1,
    \"freqUnit\":\"day\",
    \"accuracyMode\":\"gnss\",
    \"minFlightHeightM\":60,
    \"signalLostAction\":\"rth\"
  }")"
echo "$RESP" | python3 -c 'import json,sys; d=json.load(sys.stdin); print("code=%s message=%s planId=%s status=%s" % (d.get("code"),d.get("message"),d.get("data",{}).get("id"),d.get("data",{}).get("status")))'
PLAN_ID="$(echo "$RESP" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("data",{}).get("id") or "")')"
[ -n "$PLAN_ID" ] || { echo "CREATE FAILED: $RESP"; exit 1; }

echo "== 3) plan detail (expect status=pending, 3 executions, no failReason) =="
sleep 2
curl -s -m 10 "$BASE/api/v1/missions/$PLAN_ID" -H "Authorization: Bearer $TOKEN" | python3 -c '
import json,sys
d=json.load(sys.stdin)["data"]
p=d["plan"]
print("plan status=%s strategy=%s" % (p.get("status"),p.get("strategy")))
for e in d.get("executions",[])[:6]:
    print("  exec id=%s status=%s plannedAt=%s failReason=%s uuid=%s" % (e.get("id"),e.get("status"),e.get("plannedAt"),e.get("failReason"),e.get("fh2TaskUuid")))
print("totalCount=%s" % d.get("totalCount"))
'

echo "== 4) cancel cleanup =="
curl -s -m 10 -X POST "$BASE/api/v1/missions/$PLAN_ID/actions/cancel" -H "Authorization: Bearer $TOKEN" | python3 -c 'import json,sys; d=json.load(sys.stdin); print("cancel code=%s status=%s" % (d.get("code"),d.get("data",{}).get("status")))'
echo "== done =="
