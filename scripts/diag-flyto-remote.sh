#!/usr/bin/env bash
# 诊断驾驶舱"飞向此处"报 UpdateFlyToPointRequest.FlyToId required
# 目标：找到司空侧最近收到的 /flytos 请求的原始 URL 与路由细节
SUDO="sudo -S"
PW="Tksc@197653"

echo '=== docker containers ==='
echo "$PW" | $SUDO docker ps --format '{{.Names}}\t{{.Status}}' 2>/dev/null

echo
echo '=== flyto 相关日志（近 24h，每容器最多 30 行） ==='
for c in $(echo "$PW" | $SUDO docker ps --format '{{.Names}}' 2>/dev/null); do
  hits=$(echo "$PW" | $SUDO docker logs --since 24h "$c" 2>&1 | grep -iE 'flyto' | tail -30)
  if [ -n "$hits" ]; then
    echo "--- $c ---"
    echo "$hits"
    echo
  fi
done
