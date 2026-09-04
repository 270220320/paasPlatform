#!/usr/bin/env bash
# 只读查询：三个服务在 compose.yaml 中的 healthcheck 定义与端口
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0

setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 'bash -s' <<'REMOTE' 2>&1
PW='Tksc@197653'
S() { echo "$PW" | sudo -S "$@" 2>/dev/null; }

F=/opt/cloudApi2_3/install/compose.yaml

echo "===== es-task 服务段（healthcheck + ports 上下文）====="
S awk '/^  es-task:/,/^  [a-z].*:$/' "$F" | grep -A12 'healthcheck' | head -16

echo ""
echo "===== file-storage 服务段（healthcheck）====="
S awk '/^  file-storage:/,/^  [a-z].*:$/' "$F" | grep -A12 'healthcheck' | head -16

echo ""
echo "===== frontend 服务段（有无 healthcheck / 端口）====="
S awk '/^  frontend:/,/^  [a-z][a-z-]*:/' "$F" | head -50 | grep -E 'healthcheck|ports|image|FH2_FRONT' || true

echo ""
echo "===== 三个容器当前的 health 状态与最近探测结果 ====="
for c in es-task file-storage frontend; do
  echo "--- $c"
  S docker inspect "$c" --format 'State={{.State.Status}} Health={{if .State.Health}}{{.State.Health.Status}}{{else}}(无healthcheck){{end}}
LastProbe={{if .State.Health}}{{(index .State.Health.Log 0).Output}}{{end}}' 2>/dev/null
done
REMOTE
