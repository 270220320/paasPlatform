#!/usr/bin/env bash
# 司空3.1升级（十九）：提取旧compose地图服务完整挂载定义 + 对比新旧差异
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===OLD_COMPOSE_MAP_NGINX_VOLUMES===';
echo 'Tksc@197653' | sudo -S awk '/^  map-server-nginx:/{f=1} f&&/^  [a-z]/{if(!/map-server-nginx/)exit} f' /opt/cloudApi2_3/install/compose.yaml 2>/dev/null | grep -A3 'type: bind' | grep -E 'source:|target:';
echo '===OLD_COMPOSE_MAP_GL_VOLUMES===';
echo 'Tksc@197653' | sudo -S awk '/^  map-server-gl:/{f=1} f&&/^  [a-z]/{if(!/map-server-gl/)exit} f' /opt/cloudApi2_3/install/compose.yaml 2>/dev/null | grep -A3 'type: bind' | grep -E 'source:|target:';
echo '===OLD_ELEVATION_MOUNTS===';
echo 'Tksc@197653' | sudo -S awk '/^  es-digital-elevation-data:/{f=1} f&&/^  [a-z]/{if(!/es-digital-elevation-data/)exit} f' /opt/cloudApi2_3/install/compose.yaml 2>/dev/null | grep -A3 'type: bind' | grep -E 'source:|target:';
echo '===OLD_STATIC_DATA_MAP_ALL===';
echo 'Tksc@197653' | sudo -S find /opt/cloudApi2_3/data/static_data -maxdepth 4 | head -25;
echo '===ELEVATION_SIZE===';
echo 'Tksc@197653' | sudo -S du -sh /opt/cloudApi2_3/data/static_data/elevation/ 2>/dev/null;
echo '===VAR_LIB_FULL===';
echo 'Tksc@197653' | sudo -S find /var/lib/flighthub2/asset-map -maxdepth 2 | head -20;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
