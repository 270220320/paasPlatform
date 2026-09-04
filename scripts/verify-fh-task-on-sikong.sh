#!/usr/bin/env bash
# 查询 76.141 司空侧飞行任务：列表 + 指定 uuid 详情（鉴权对齐 SDK：auth_keys + user_id + x-auth-token）
set -euo pipefail
GATEWAY="http://192.168.76.141:30812/gateway"
TOKEN="eyJhbGciOiJIUzUxMiIsImtpZCI6IjU3YmQyNmEwLTYyMDktNGE5My1hNjg4LWY4NzUyYmU1ZDE5MSIsInR5cCI6IkpXVCJ9.eyJhY2NvdW50IjoiZGVmYXVsdCIsImV4cCI6MTkyNDgxOTIwMCwibmJmIjoxNjA5NDU5MjAwLCJvcmdhbml6YXRpb25fdXVpZCI6IjVhNmY5ZDRiLTJhMzgtNGI0Yi04NmY5LTNhNjc4ZGEwYmY4OCIsInByb2plY3RfdXVpZCI6IiIsInN1YiI6ImZoMiIsInVzZXJfaWQiOiJkZWZhdWx0In0.psKQTfTOEVB9fn0OrBW4puoHzIV6C9pgnwk6JqEjpwoPE4D1DDiLAuCbMcH_hpnb8DUZrWvNI6d6X7hQTHzEnQ"
UUID="34fa93cd-3788-4e28-ab14-a895e8961485"

echo "== 1) 任务详情 GET /atc/api/v1/flight-tasks/$UUID =="
curl -s -m 15 "$GATEWAY/atc/api/v1/flight-tasks/$UUID" \
  -H "auth_keys: user_id" \
  -H "user_id: paas_test" \
  -H "x-auth-token: $TOKEN" | python3 -m json.tool

echo "== 2) 任务列表（带 token，page_size=20）=="
curl -s -m 15 -G "$GATEWAY/atc/api/v1/flight-tasks" \
  --data-urlencode "page=1" \
  --data-urlencode "page_size=20" \
  -H "auth_keys: user_id" \
  -H "user_id: paas_test" \
  -H "x-auth-token: $TOKEN" | python3 -m json.tool | head -120

echo "== 3) 列表带 status=5 过滤 =="
curl -s -m 15 -G "$GATEWAY/atc/api/v1/flight-tasks" \
  --data-urlencode "page=1" \
  --data-urlencode "page_size=5" \
  --data-urlencode "task_type=1" \
  --data-urlencode "status=5" \
  -H "auth_keys: user_id" \
  -H "user_id: paas_test" \
  -H "x-auth-token: $TOKEN" | python3 -m json.tool | head -60
