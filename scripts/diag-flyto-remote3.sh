#!/usr/bin/env bash
# 抓取 es-task 中 fly-to-points 全部请求日志（含创建 POST 与更新 PUT），确认创建响应是否含 fly_to_id
SUDO="sudo -S"
PW="Tksc@197653"

echo '===== es-task: fly-to-points 全部请求（近 6h，不截断） ====='
echo "$PW" | $SUDO docker logs --since 6h es-task 2>&1 | grep 'fly-to-points' | head -40

echo
echo '===== es-task: fly_to_point 事件链（近 6h） ====='
echo "$PW" | $SUDO docker logs --since 6h es-task 2>&1 | grep -iE 'FlyToProgress|flyto_task_event|fly_to_point' | grep -v 'fly-to-points' | head -40

echo
echo '===== ts-gateway: fly 相关（近 6h） ====='
echo "$PW" | $SUDO docker logs --since 6h ts-gateway 2>&1 | grep -iE 'fly.to.point|flyto' | head -30
