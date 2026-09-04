#!/usr/bin/env python3
import re

src = open('/tmp/dm_sdk2.js', encoding='utf-8', errors='ignore').read()
print('file size:', len(src))

# 1. 所有出现 cloud_environment_switch / cloud-environment-switch 的位置及上下文
for m in re.finditer(r'cloud[-_]environment[-_]switch', src):
    s, e = max(0, m.start() - 300), min(len(src), m.end() + 400)
    print('\n=== SWITCH CALL @', m.start(), '===')
    print(src[s:e].replace('\n', ' ')[:900])

# 2. thirdForm 数据初始化（字段默认值）
for m in re.finditer(r'thirdForm\s*[:=]\s*\{', src):
    s = m.start()
    print('\n=== THIRDFORM INIT @', s, '===')
    print(src[s:s + 500].replace('\n', ' '))

# 3. api2Form 初始化
for m in re.finditer(r'api2Form\s*[:=]\s*\{', src):
    s = m.start()
    print('\n=== API2FORM INIT @', s, '===')
    print(src[s:s + 500].replace('\n', ' '))
