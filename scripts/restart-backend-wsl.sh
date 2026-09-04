#!/usr/bin/env bash
# 重启 WSL 后端：先杀旧进程，再用 run-backend-wsl.sh 启动。
# 注意：从 Windows 调 wsl.exe 时，会话退出后 WSL 实例会关停，nohup 后台进程会被一并杀掉。
# 正确姿势：在持久后台终端里直接前台运行本脚本或 run-backend-wsl.sh，保持 wsl 会话存活。
set -uo pipefail
pkill -f "spring-boot:run" 2>/dev/null
pkill -f "UavPlatformApplication" 2>/dev/null
sleep 2
cd /mnt/d/AiFlow
exec bash scripts/run-backend-wsl.sh
