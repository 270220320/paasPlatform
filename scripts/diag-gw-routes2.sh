#!/usr/bin/env bash
# 1) 探测网关 task-management 路径 2) 前端JS找任务接口真实调用串
echo "=== probe: /gateway/task-management/ (30812) ==="
for p in "task/list.rest" "task/create.rest"; do
  code=$(curl -s -o /tmp/_pb1 -w "%{http_code}" -m 6 -X POST -H "Content-Type: application/json" -H "user_id: paas_test" -d "{}" "http://192.168.76.141:30812/gateway/task-management/$p")
  echo "30812 /gateway/task-management/$p -> $code : $(head -c 150 /tmp/_pb1)"
done
echo "=== probe: 80 port /task-management/ ==="
code=$(curl -s -o /tmp/_pb2 -w "%{http_code}" -m 6 -X POST -H "Content-Type: application/json" -H "user_id: paas_test" -d "{}" "http://192.168.76.141/task-management/task/list.rest")
echo "80 /task-management/task/list.rest -> $code : $(head -c 150 /tmp/_pb2)"

cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo "=== F) frontend JS: task-management refs ==="
docker exec frontend sh -c 'grep -rhoaE "[\\x22\x27/][a-z0-9/_-]{0,30}task-management[a-z0-9/_-]{0,30}" /usr/share/nginx/html/static/js/ 2>/dev/null | sort -u | head -20'
echo "=== G) frontend JS: task api calls (create/list) ==="
docker exec frontend sh -c 'grep -rhoaE "[\\x22\x27](/api/|/gateway/)?[a-z0-9/_-]{0,40}task[a-z0-9/_.-]{0,40}" /usr/share/nginx/html/static/js/ 2>/dev/null | grep -aiE "create|list|plan" | sort -u | head -30'
echo DONE
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo Tksc@197653 | sudo -S bash -c 'echo $R | base64 -d | bash'"
