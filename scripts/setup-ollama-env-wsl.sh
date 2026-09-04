#!/bin/bash
# 为 WSL 配置 OLLAMA_BASE_URL，指向 Windows 宿主机上的 Ollama（UAVPlatform 对话控制模块用）。
# 网关 IP 每次随 WSL 网络重启可能漂移，因此在 .bashrc 中动态解析，而非写死。
set -e

if ! grep -q "OLLAMA_BASE_URL" ~/.bashrc; then
  printf '\n# Ollama on Windows host (auto gateway IP, for UAVPlatform chat module)\nexport OLLAMA_BASE_URL="http://$(ip route show default | awk \x27{print $3}\x27):11434"\n' >> ~/.bashrc
  echo "[ok] appended OLLAMA_BASE_URL to ~/.bashrc"
else
  echo "[skip] OLLAMA_BASE_URL already in ~/.bashrc"
fi

# 立即导出并验证
export OLLAMA_BASE_URL="http://$(ip route show default | awk '{print $3}'):11434"
echo "OLLAMA_BASE_URL=$OLLAMA_BASE_URL"
echo -n "probe: "
curl -sS --max-time 6 "$OLLAMA_BASE_URL/api/tags" | head -c 120
echo
