#!/usr/bin/env python3
import re

src = open('/tmp/dm_sdk2.js', encoding='utf-8', errors='ignore').read()

# 1. third_cloud_config 的 body 组装
for m in re.finditer(r'third_cloud_config', src):
    s = max(0, m.start() - 500)
    print('\n=== third_cloud_config @', m.start(), '===')
    print(src[s:m.end() + 300].replace('\n', ' '))

# 2. mqtt_addr 组装
for m in list(re.finditer(r'mqtt_addr', src))[:8]:
    s = max(0, m.start() - 250)
    print('\n=== mqtt_addr @', m.start(), '===')
    print(src[s:m.end() + 250].replace('\n', ' '))
