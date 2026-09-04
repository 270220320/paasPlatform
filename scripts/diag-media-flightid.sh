#!/usr/bin/env bash
# 验证司空媒体库的 flight_id 归属：任务 uuid vs 设备侧 bid
set -euo pipefail
GW="http://192.168.76.141:30812/gateway"
TOKEN="eyJhbGciOiJIUzUxMiIsImtpZCI6IjU3YmQyNmEwLTYyMDktNGE5My1hNjg4LWY4NzUyYmU1ZDE5MSIsInR5cCI6IkpXVCJ9.eyJhY2NvdW50IjoiZGVmYXVsdCIsImV4cCI6MTkyNDgxOTIwMCwibmJmIjoxNjA5NDU5MjAwLCJvcmdhbml6YXRpb25fdXVpZCI6IjVhNmY5ZDRiLTJhMzgtNGI0Yi04NmY5LTNhNjc4ZGEwYmY4OCIsInByb2plY3RfdXVpZCI6IiIsInN1YiI6ImZoMiIsInVzZXJfaWQiOiJkZWZhdWx0In0.psKQTfTOEVB9fn0OrBW4puoHzIV6C9pgnwk6JqEjpwoPE4D1DDiLAuCbMcH_hpnb8DUZrWvNI6d6X7hQTHzEnQ"

for FID in "34fa93cd-3788-4e28-ab14-a895e8961485" "71188b31-7b62-4bac-be87-5354edec9e33"; do
  echo "== flight_id=$FID (types 2,3) =="
  curl -s -m 15 -G "$GW/pf-media/api/v1/media-files/list" \
    --data-urlencode "flight_id=$FID" \
    --data-urlencode "start_index=0" \
    --data-urlencode "page_size=50" \
    --data-urlencode "file_types=2" \
    --data-urlencode "file_types=3" \
    -H "auth_keys: user_id" -H "user_id: paas_test" -H "x-auth-token: $TOKEN" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); lst=(d.get("data") or {}).get("list") or []; print("code:", d.get("code"), "count:", len(lst)); [print("  -", f.get("file_id"), f.get("file_name"), "flight_id:", f.get("flight_id")) for f in lst[:8]]'
done

echo "== 不过滤类型，查 dock 全部媒体（近 20 条）=="
curl -s -m 15 -G "$GW/pf-media/api/v1/media-files/list" \
  --data-urlencode "start_index=0" \
  --data-urlencode "page_size=20" \
  --data-urlencode "file_types=2" \
  --data-urlencode "file_types=3" \
  -H "auth_keys: user_id" -H "user_id: paas_test" -H "x-auth-token: $TOKEN" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); lst=(d.get("data") or {}).get("list") or []; print("code:", d.get("code"), "count:", len(lst)); [print("  -", f.get("file_name"), "| flight_id:", f.get("flight_id"), "| created:", f.get("created_at")) for f in lst[:20]]'
