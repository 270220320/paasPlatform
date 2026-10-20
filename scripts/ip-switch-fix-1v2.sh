#!/usr/bin/env bash
# 2026-09-21 IP 切换修复1v2：备份 + sed 替换（显式清单，修正 $S 变量词法坑）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
FILES="
/opt/cloudApi2_3.1/install/compose.yaml
/opt/cloudApi2_3.1/install/conf/middleware-service/alertmanager/config/alertmanager.yml
/opt/cloudApi2_3.1/install/conf/middleware-service/prometheus/config/prometheus.yml
/opt/cloudApi2_3.1/install/conf/middleware-service/grafana/dashboards/fh2-log-monitoring-template.json
/opt/cloudApi2_3.1/install/conf/middleware-service/grafana/config/grafana.ini
/opt/cloudApi2_3.1/install/conf/middleware-service/loki/loki-config.yaml
/opt/cloudApi2_3.1/install/conf/middleware-service/traefik/dynamic_conf.yaml
/opt/cloudApi2_3.1/install/conf/self-service/devops/product.yaml
/opt/cloudApi2_3.1/install/conf/self-service/devops/nginx-fe.conf
/opt/cloudApi2_3.1/install/deployment/serverless/serverless-aec-volume-calculate/values.yaml
/opt/cloudApi2_3.1/install/deployment/serverless/serverless-aec-designfile-dxf/values.yaml
/opt/cloudApi2_3.1/install/deployment/serverless/k3s_manifest/300-promtail.yaml
/opt/cloudApi2_3.1/install/deployment/serverless/serverless-aec-line-profile/values.yaml
/opt/cloudApi2_3.1/install/deployment/serverless/aec-pdf/values.yaml
/opt/cloudApi2_3.1/install/deployment/serverless/serverless-aec-report-snapshot/values.yaml
/opt/cloudApi2_3.1/install/deployment/serverless/serverless-report-generation/values.yaml
/var/lib/flighthub2/asset-map/map_config.yaml
"
echo "===== 0. env file check (.envpath target) ====="
echo "Tksc@197653" | sudo -S grep -n "76\.141" /opt/cloudApi2_3.1/fh2-privatization-install-packages/env 2>/dev/null || echo "env file: no residue (or unreadable)"
echo "===== 1. BACKUP ====="
BK=/opt/backup-ip141-20260921
echo "Tksc@197653" | sudo -S mkdir -p $BK
for f in $FILES; do
  if echo "Tksc@197653" | sudo -S test -f "$f"; then
    echo "Tksc@197653" | sudo -S cp -a --parents "$f" $BK/ && echo "backed: $f"
  else
    echo "NOT FOUND: $f"
  fi
done
echo "===== 2. SED REPLACE ====="
for f in $FILES; do
  if echo "Tksc@197653" | sudo -S test -f "$f"; then
    echo "Tksc@197653" | sudo -S sed -i 's/192\.168\.76\.141/192.168.76.129/g' "$f" && echo "replaced: $f"
  fi
done
echo "===== 3. ENV FILE REPLACE (if residue) ====="
if echo "Tksc@197653" | sudo -S grep -q "76\.141" /opt/cloudApi2_3.1/fh2-privatization-install-packages/env 2>/dev/null; then
  echo "Tksc@197653" | sudo -S cp -a --parents /opt/cloudApi2_3.1/fh2-privatization-install-packages/env $BK/ && echo "backed: env"
  echo "Tksc@197653" | sudo -S sed -i 's/192\.168\.76\.141/192.168.76.129/g' /opt/cloudApi2_3.1/fh2-privatization-install-packages/env && echo "replaced: env"
else
  echo "env: no replace needed"
fi
echo "===== 4. VERIFY residue (whole /opt/cloudApi2_3.1 + asset-map) ====="
echo "Tksc@197653" | sudo -S grep -rl "76\.141" /opt/cloudApi2_3.1/ /var/lib/flighthub2/asset-map/ 2>/dev/null | grep -vE "\.log$|backup-ip141" || echo "CLEAN: no residue left"
echo "===== 5. map_config.yaml after ====="
echo "Tksc@197653" | sudo -S cat /var/lib/flighthub2/asset-map/map_config.yaml
echo "===== FIX1v2 DONE ====="
REMOTE
