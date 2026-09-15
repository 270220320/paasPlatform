#!/usr/bin/env bash
# 停掉 WSL 里旧的 uav-platform 后端（保底脚本，避免 PowerShell 转义坑）
ps aux | grep UavPlatform | grep -v grep | awk '{print $2}' > /tmp/kill.pid
echo "pids: $(cat /tmp/kill.pid | tr '\n' ' ')"
xargs -r kill < /tmp/kill.pid 2>/dev/null
sleep 3
LEFT=$(ps aux | grep UavPlatform | grep -v grep | wc -l)
echo "remaining: $LEFT"
