#!/usr/bin/env bash
# Optional helper. Plain `mvn spring-boot:run` in WSL also works (auto gateway).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -z "${MYSQL_HOST:-}" ]]; then
  MYSQL_HOST="$(ip route show default | awk '{print $3; exit}')"
  export MYSQL_HOST
fi
echo "MYSQL_HOST=$MYSQL_HOST"
cd "$ROOT/backend"
exec mvn spring-boot:run "$@"
