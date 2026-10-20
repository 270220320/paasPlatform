#!/usr/bin/env bash
# Wait until the WSL backend answers on 8080 (any HTTP status means it is up).
for i in $(seq 1 24); do
  sleep 5
  CODE=$(curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:8080/api/v1/auth/me 2>/dev/null)
  echo "try$i: $CODE"
  if [ "$CODE" != "000" ] && [ -n "$CODE" ]; then
    echo "BACKEND_UP (http=$CODE)"
    exit 0
  fi
done
echo "BACKEND_TIMEOUT"
exit 1
