#!/usr/bin/env bash
# 诊断 POI 环绕失败第4步：绕过损坏的 docker 日志流，直接读 es-task 日志文件
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "bash -s" <<'REMOTE'
echo "Tksc@197653" | sudo -S -v 2>/dev/null
sudo bash <<'ROOT'
LOGPATH=$(docker inspect es-task --format '{{.LogPath}}' 2>/dev/null)
echo "===== LOGPATH: $LOGPATH ====="
ls -lh "$LOGPATH"
echo "===== POI LINES (cleaned) ====="
tr -d '\000' < "$LOGPATH" | grep -a "poi" | grep -av "point" | tail -50
echo "===== DONE ====="
ROOT
REMOTE
