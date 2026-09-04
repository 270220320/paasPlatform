#!/usr/bin/env bash
# 端到端验证：session 3 历史里有旧的 device_list 空结果（已超 TTL），
# 再次询问设备时应重新调用工具并返回真实设备
set -euo pipefail
BASE="http://127.0.0.1:8080"

LOGIN="$(curl -s -m 10 -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin@aiflow.local","password":"123456"}')"
TOKEN="$(echo "$LOGIN" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')"
echo "== token ok =="

echo "== SSE 对话：现在有哪些设备（最长等 150s）=="
curl -s -N -m 150 -X POST "$BASE/api/v1/chat/sessions/3/messages" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"content":"现在工作空间里有哪些设备？"}' \
  | grep -oE '"type":"[a-z_]+"' | sort | uniq -c
echo "== done =="
