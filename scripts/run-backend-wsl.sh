#!/usr/bin/env bash
# Optional helper. Plain `mvn spring-boot:run` in WSL also works (auto gateway).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -z "${MYSQL_HOST:-}" ]]; then
  MYSQL_HOST="$(ip route show default | awk '{print $3; exit}')"
  export MYSQL_HOST
fi
echo "MYSQL_HOST=$MYSQL_HOST"
# Ollama 在 Windows 宿主机上；未显式导出时自动指向网关（后端 ChatProperties 亦有启动期自动探测兜底）
if [[ -z "${OLLAMA_BASE_URL:-}" ]]; then
  OLLAMA_BASE_URL="http://$(ip route show default | awk '{print $3; exit}'):11434"
  export OLLAMA_BASE_URL
fi
echo "OLLAMA_BASE_URL=$OLLAMA_BASE_URL"
cd "$ROOT/backend"
exec mvn spring-boot:run "$@"
