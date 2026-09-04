#!/usr/bin/env bash
# 验证 device_list 修复：登录后查询 /api/v1/paas/devices/remote（走 ReflectiveFh2SdkClient.listDevices）
set -euo pipefail
BASE="http://127.0.0.1:8080"

LOGIN="$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin@aiflow.local","password":"123456"}')"
echo "== login resp (first 300 chars) =="
echo "${LOGIN:0:300}"

TOKEN="$(echo "$LOGIN" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])' 2>/dev/null || true)"
if [[ -z "$TOKEN" ]]; then
  echo "TOKEN parse failed, raw login: $LOGIN" >&2
  exit 1
fi
echo "== token ok (len=${#TOKEN}) =="

echo "== /api/v1/paas/devices/remote =="
curl -s -m 45 "$BASE/api/v1/paas/devices/remote" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
