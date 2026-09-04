#!/usr/bin/env bash
# 抓取 fly-to-points 的 POST 创建请求（含响应体，确认是否返回 fly_to_id）
SUDO="sudo -S"
PW="Tksc@197653"

echo '===== es-task: fly-to-points POST 创建请求（近 6h） ====='
echo "$PW" | $SUDO docker logs --since 6h es-task 2>&1 | grep 'fly-to-points' | grep -iE 'method=POST|"method":"POST"' | head -10

echo
echo '===== es-task: 06:03:4x 时段全部 API 日志 ====='
echo "$PW" | $SUDO docker logs --since 6h es-task 2>&1 | grep -E '06:03:4[0-9]' | grep 'label=API' | head -20
