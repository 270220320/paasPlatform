#!/usr/bin/env bash
# 司空3.1升级（十六）：确认旧地图挂载方式 + 新目录现状
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===OLD_COMPOSE_MAP_MOUNTS===';
echo 'Tksc@197653' | sudo -S grep -B5 -A20 'map-server-nginx:' /opt/cloudApi2_3/install/compose.yaml 2>/dev/null | grep -E 'volumes|static_data|asset-map|png-tiles|street-tiles|source|device|-' | head -20;
echo '===OLD_COMPOSE_GL_MOUNTS===';
echo 'Tksc@197653' | sudo -S grep -B5 -A20 'map-server-gl:' /opt/cloudApi2_3/install/compose.yaml 2>/dev/null | grep -E 'volumes|static_data|asset-map|mbtiles|tilesets|-' | head -15;
echo '===NEW_ASSET_MAP_DIR===';
echo 'Tksc@197653' | sudo -S ls -la /var/lib/flighthub2/asset-map/ 2>&1;
echo 'Tksc@197653' | sudo -S du -sh /var/lib/flighthub2/asset-map/* 2>/dev/null;
echo '===OLD_ASSET_MAP_STRUCTURE===';
echo 'Tksc@197653' | sudo -S ls -la /opt/cloudApi2_3/data/static_data/asset-map/ 2>/dev/null;
echo 'Tksc@197653' | sudo -S du -sh /opt/cloudApi2_3/data/static_data/asset-map/* 2>/dev/null;
echo '===STATIC_DATA_31===';
echo 'Tksc@197653' | sudo -S ls -la /opt/cloudApi2_3.1/data/static_data/ 2>/dev/null;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
