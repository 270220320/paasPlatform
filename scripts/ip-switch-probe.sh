#!/usr/bin/env bash
# 探测新旧 IP 占用情况：连续 ping + 端口对比
for ip in 192.168.76.129 192.168.76.141; do
  echo "== $ip =="
  ping -c 4 -W 1 "$ip" 2>&1 | tail -1
  for port in 22 30812; do
    if timeout 4 bash -c "echo > /dev/tcp/$ip/$port" 2>/dev/null; then
      echo "port$port OPEN"
    else
      echo "port$port REFUSED/FILTERED"
    fi
  done
done
echo "== ARP 表 =="
ip neigh | grep -E "76\.129|76\.141" || true
