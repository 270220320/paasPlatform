#!/usr/bin/env bash
# 短周期探测 76.129:22 是否恢复（60s 内 6 次）
for i in 1 2 3 4 5 6; do
  ts=$(date +%H:%M:%S)
  if timeout 4 bash -c "echo > /dev/tcp/192.168.76.129/22" 2>/dev/null; then
    echo "$ts try$i: port22 OPEN"
  else
    echo "$s try$i: port22 REFUSED"
  fi
  [ "$i" -lt 6 ] && sleep 10
done
echo "== final arp =="
ip neigh show 192.168.76.129 || true
