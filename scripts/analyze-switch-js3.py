#!/usr/bin/env python3
import re

src = open('/tmp/dm_sdk2.js', encoding='utf-8', errors='ignore').read()

# 1. F_ 枚举定义（fh2/third/private/api2 的数值）
for pat in [r'F_\s*=', r'fh2[=:]', r'\(self_exports\.fh2|exports\.fh2']:
    for m in list(re.finditer(pat, src))[:6]:
        s = max(0, m.start() - 120)
        seg = src[s:m.end() + 400].replace('\n', ' ')
        if 'third' in seg or 'fh2' in seg or 'private' in seg:
            print('\n=== ENUM @', m.start(), '===')
            print(seg[:600])

# 2. 表单校验 rules：mqtt 地址 pattern
for m in list(re.finditer(r'(rules|pattern|validator)', src[27200000:27400000]))[:1]:
    pass
seg = src[27220000:27260000]
for m in re.finditer(r'pattern:[^}]{0,120}', seg):
    print('\n=== PATTERN ===')
    print(m.group(0)[:200])

# 3. cloud_switch 相关中文提示文案
for m in list(re.finditer(r'cloud_switch_modal', src))[:4]:
    pass
for m in re.finditer(r'"zh":"[^"]*(格式|地址|tcp|mqtt|以.{0,6}开头)[^"]*"', src):
    seg = m.group(0)
    if 'mqtt' in seg.lower() or 'tcp' in seg.lower() or '格式' in seg:
        print('\n=== ZH HINT ===', seg[:220])
