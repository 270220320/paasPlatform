#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""解析实测 tools/list JSON，输出每个工具的 confirm/dry_run/action 事实清单"""
import json
import re
import sys

SRC = r"C:\Users\meng\.qoder\cache\projects\AiFlow-9ed8816f\agent-tools\task-844\d253de16.txt"

text = open(SRC, encoding="utf-8").read()
# 找到 id:2 的 tools/list 响应行：data:{"jsonrpc":"2.0","id":2,"result":{"tools":[...]}}
m = re.search(r'data:(\{"jsonrpc":"2\.0","id":2,"result":\{"tools".*)', text)
if not m:
    print("tools/list response not found")
    sys.exit(1)
raw = m.group(1).strip()
resp = json.loads(raw)
tools = resp["result"]["tools"]
print(f"total tools: {len(tools)}\n")

for t in tools:
    schema = t.get("inputSchema", {})
    props = schema.get("properties", {})
    required = schema.get("required", [])
    has_confirm = "confirm" in props
    confirm_req = "confirm" in required
    has_dry = "dry_run" in props
    actions = props.get("action", {}).get("enum") or props.get("direction", {}).get("enum") or []
    no_args = not props
    print(f"### {t['name']}")
    print(f"  confirm_in_schema={has_confirm} confirm_required={confirm_req} dry_run={has_dry} no_args={no_args}")
    if actions:
        print(f"  actions({len(actions)}): {','.join(actions)}")
    print(f"  required: {required}")
