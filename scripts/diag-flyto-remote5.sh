#!/usr/bin/env bash
# 抓取 in-flight 查询日志（用户点击时段 06:07-06:08），确认返回内容
SUDO="sudo -S"
PW="Tksc@197653"

echo '===== es-task: in-flight 请求日志（近 6h，重点看 response） ====='
echo "$PW" | $SUDO docker logs --since 6h es-task 2>&1 | grep 'in-flight' | grep -E '06:0[3-9]' | head -12

echo
echo '===== 实测 in-flight 接口（user_id 鉴权） ====='
echo "$PW" | $SUDO docker exec es-task sh -c 'wget -qO- --header="auth_keys: user_id" --header="user_id: paas_test" "http://127.0.0.1:8080/atc/api/v1/flight-tasks/in-flight?sn=8UUXN2K00A0144" 2>&1 || curl -s -m 8 -H "auth_keys: user_id" -H "user_id: paas_test" "http://127.0.0.1:8080/atc/api/v1/flight-tasks/in-flight?sn=8UUXN2K00A0144"' 2>&1 | head -5
