#!/usr/bin/env bash
# 精确抓取用户操作时段（UTC 06:00-06:15）内 flyto 请求的完整链路日志
SUDO="sudo -S"
PW="Tksc@197653"

echo '===== pf-gateway-service: flyto/FlyToId 相关（近 6h） ====='
echo "$PW" | $SUDO docker logs --since 6h pf-gateway-service 2>&1 | grep -iE 'flyto|fly_to|FlyToId' | tail -60

echo
echo '===== pf-control-core: flyto/FlyToId/validation 相关（近 6h） ====='
echo "$PW" | $SUDO docker logs --since 6h pf-control-core 2>&1 | grep -iE 'flyto|fly_to|FlyToId|validation|required' | tail -80

echo
echo '===== pf-wayline-service: flyto 相关（近 6h） ====='
echo "$PW" | $SUDO docker logs --since 6h pf-wayline-service 2>&1 | grep -iE 'flyto|fly_to|FlyToId' | tail -60

echo
echo '===== pf-atc-service: flyto 相关（近 6h） ====='
echo "$PW" | $SUDO docker logs --since 6h pf-atc-service 2>&1 | grep -iE 'flyto|fly_to|FlyToId' | tail -60

echo
echo '===== 全容器搜索 UpdateFlyToPoint 字样（近 24h） ====='
for c in $(echo "$PW" | $SUDO docker ps --format '{{.Names}}' 2>/dev/null); do
  hits=$(echo "$PW" | $SUDO docker logs --since 24h "$c" 2>&1 | grep -F 'UpdateFlyToPoint' | tail -20)
  if [ -n "$hits" ]; then
    echo "--- $c ---"
    echo "$hits"
    echo
  fi
done

echo
echo '===== pf-control-core 06:02-06:08 UTC 全量日志（时间切片） ====='
echo "$PW" | $SUDO docker logs --since 6h pf-control-core 2>&1 | grep -E '2026-09-03T06:0[2-8]' | head -120
