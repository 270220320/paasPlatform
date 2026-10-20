#!/usr/bin/env bash
# AiFlow API smoke test, mapped to docs/05-test/smoke-cases.md
# Coverage: SC-001 auth chain | SC-002 wayline create/publish | SC-004 media list (read-only) | SC-005 bind-code & devices (read-only)
# SC-003 (real flight mission dispatch) is EXCLUDED on purpose: it would trigger a real dock/drone flight.
# Usage: wsl -e bash /mnt/d/AiFlow/scripts/smoke_api.sh
set -u
BASE=http://127.0.0.1:8080/api/v1
PASS=0; FAIL=0
ok()  { echo "  [PASS] $1"; PASS=$((PASS+1)); }
bad() { echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }

jget() { # read stdin JSON, eval python expr against it, e.g. jget '["data"]["id"]'
  python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    v = d$1
    print('' if v is None else v)
except Exception:
    print('')"
}

api_post() { # path json [token]
  curl -s -m 10 -X POST -H 'Content-Type: application/json' ${3:+-H "Authorization: Bearer $3"} -d "$2" "$BASE$1"
}
api_get() { curl -s -m 10 ${2:+-H "Authorization: Bearer $2"} "$BASE$1"; }
api_put() { curl -s -m 10 -X PUT -H 'Content-Type: application/json' ${3:+-H "Authorization: Bearer $3"} -d "$2" "$BASE$1"; }
api_del() { curl -s -m 10 -X DELETE ${2:+-H "Authorization: Bearer $2"} "$BASE$1"; }

echo "=== AiFlow API Smoke Test ==="
echo "target: $BASE   time: $(date '+%F %T')"

# ---------- SC-001 auth chain ----------
echo ""
echo "--- SC-001 auth: login / me / logout / stale-token ---"
LOGIN=$(api_post /auth/login '{"account":"admin@aiflow.local","password":"admin123"}')
CODE=$(echo "$LOGIN" | jget '["code"]')
if [ "$CODE" = "0" ]; then ok "login code=0"; else bad "login code=$CODE resp=${LOGIN:0:200}"; fi
TOKEN=$(echo "$LOGIN" | jget '["data"]["accessToken"]')
if [ -n "$TOKEN" ]; then ok "accessToken acquired"; else bad "no accessToken resp=${LOGIN:0:200}"; fi

if [ -n "$TOKEN" ]; then
  ME=$(api_get /auth/me "$TOKEN")
  MECODE=$(echo "$ME" | jget '["code"]')
  if [ "$MECODE" = "0" ] && echo "$ME" | grep -q 'super_admin'; then
    ok "me returns super_admin"
  else
    bad "me code=$MECODE resp=${ME:0:200}"
  fi

  api_post /auth/logout '{}' "$TOKEN" >/dev/null
  STALE=$(api_get /auth/me "$TOKEN")
  STALECODE=$(echo "$STALE" | jget '["code"]')
  if [ "$STALECODE" = "40100" ]; then
    ok "stale token rejected with 40100"
  else
    bad "stale token code=$STALECODE (expected 40100) resp=${STALE:0:200}"
  fi

  # re-login: SC-001 logout invalidated the token, later cases need a fresh one
  LOGIN2=$(api_post /auth/login '{"account":"admin@aiflow.local","password":"admin123"}')
  TOKEN=$(echo "$LOGIN2" | jget '["data"]["accessToken"]')
  if [ -n "$TOKEN" ]; then ok "re-login for later cases"; else bad "re-login failed resp=${LOGIN2:0:200}"; fi
fi

# ---------- SC-002 wayline create & publish ----------
echo ""
echo "--- SC-002 wayline: folder -> create -> publish -> verify -> cleanup ---"
TS=$(date '+%m%d%H%M')
if [ -n "$TOKEN" ]; then
  FOLDER=$(api_post /wayline-folders "{\"name\":\"smoke-$TS\"}" "$TOKEN")
  FID=$(echo "$FOLDER" | jget '["data"]["id"]')
  if [ -n "$FID" ]; then ok "folder created id=$FID"; else bad "folder create resp=${FOLDER:0:200}"; fi

  WID=""
  if [ -n "$FID" ]; then
    WL=$(api_post /waylines "{\"name\":\"SC02-$TS\",\"waylineType\":\"waypoint\",\"status\":\"draft\",\"aircraftModel\":\"M350RTK\",\"folderId\":$FID}" "$TOKEN")
    WID=$(echo "$WL" | jget '["data"]["id"]')
    WSTATUS=$(echo "$WL" | jget '["data"]["status"]')
    if [ -n "$WID" ] && [ "$WSTATUS" = "draft" ]; then ok "wayline created id=$WID status=draft"; else bad "wayline create resp=${WL:0:200}"; fi
  fi

  if [ -n "$WID" ]; then
    UP=$(api_put "/waylines/$WID" '{"status":"published"}' "$TOKEN")
    DET=$(api_get "/waylines/$WID" "$TOKEN")
    PST=$(echo "$DET" | jget '["data"]["status"]')
    if [ "$PST" = "published" ]; then ok "wayline published (detail status=published)"; else bad "publish failed, detail status=$PST put=${UP:0:200}"; fi
    api_del "/waylines/$WID" "$TOKEN" >/dev/null && ok "cleanup: wayline deleted" || bad "cleanup: wayline delete failed"
  fi
  if [ -n "$FID" ]; then
    api_del "/wayline-folders/$FID" "$TOKEN" >/dev/null && ok "cleanup: folder deleted" || bad "cleanup: folder delete failed"
  fi
else
  bad "SC-002 skipped: no token"
fi

# ---------- SC-004 media library (read-only) ----------
echo ""
echo "--- SC-004 media: list (read-only) ---"
if [ -n "$TOKEN" ]; then
  MEDIA=$(api_get '/library/media?page=1&pageSize=5' "$TOKEN")
  MCODE=$(echo "$MEDIA" | jget '["code"]')
  if [ "$MCODE" = "0" ]; then ok "media list code=0"; else bad "media list code=$MCODE resp=${MEDIA:0:200}"; fi
else
  bad "SC-004 skipped: no token"
fi

# ---------- SC-005 bind-code & devices (read-only) ----------
echo ""
echo "--- SC-005 device: bind-codes/current + devices (read-only) ---"
if [ -n "$TOKEN" ]; then
  BIND=$(api_get /bind-codes/current "$TOKEN")
  BCODE=$(echo "$BIND" | jget '["code"]')
  if [ "$BCODE" = "0" ]; then ok "bind-codes/current code=0"; else bad "bind-codes code=$BCODE resp=${BIND:0:200}"; fi
  DEV=$(api_get /devices "$TOKEN")
  DCODE=$(echo "$DEV" | jget '["code"]')
  DN=$(echo "$DEV" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data')
    n = len(data) if isinstance(data, list) else (data.get('total', '?') if isinstance(data, dict) else '?')
    print(n)
except Exception:
    print('?')" )
  if [ "$DCODE" = "0" ]; then ok "devices code=0 (count=$DN)"; else bad "devices code=$DCODE resp=${DEV:0:200}"; fi
else
  bad "SC-005 skipped: no token"
fi

# ---------- summary ----------
echo ""
echo "================ SUMMARY ================"
echo "PASS=$PASS FAIL=$FAIL"
echo "SC-003 (real mission dispatch) NOT executed: requires bound online dock + pilot confirmation."
[ "$FAIL" -eq 0 ] && echo "SMOKE RESULT: GREEN" || echo "SMOKE RESULT: RED"
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
