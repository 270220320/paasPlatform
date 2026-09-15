#!/usr/bin/env bash
# WSL 长驻启动 AiFlow 后端（配套 _stop_backend.sh）
cd /mnt/d/AiFlow/backend || exit 1
# WSL2 访问 Windows 本机 MySQL：默认网关即宿主机
export MYSQL_HOST=$(ip route show default | awk '{print $3}')
echo "MYSQL_HOST=$MYSQL_HOST"
exec mvn spring-boot:run 2>&1 | tee -a logs/restart-console-$(date +%Y%m%d).log
