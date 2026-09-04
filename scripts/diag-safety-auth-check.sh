#!/usr/bin/env bash
# 检查 WSL 后端启动状态
ls -la /tmp/backend-start.log 2>/dev/null || echo NO_LOG
ps aux | grep -E 'java|mvn' | grep -v grep | awk '{print $2, $11, $12}' | head -5
echo ----
if [ -f /tmp/backend-start.log ]; then
  grep -E 'native SDK initialized|Started UavPlatformApplication|ERROR' /tmp/backend-start.log | tail -12
fi
echo ----
ss -tln | grep 8080 || echo NOT_LISTENING_YET
