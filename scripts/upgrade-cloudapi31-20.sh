#!/usr/bin/env bash
# 司空3.1升级（二十）：迁移旧目录地图数据（mbtiles + elevation）到 3.1 服务位置
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===MBTILES_DIFF_CHECK===';
echo 'Tksc@197653' | sudo -S md5sum /opt/cloudApi2_3/data/static_data/asset-map/mbtiles/satellite-2017-11-01-china_shenzhen.mbtiles /opt/cloudApi2_3.1/data/static_data/asset-map/mbtiles/satellite-2017-11-01-china_shenzhen.mbtiles 2>&1;
echo '===ELEVATION_COMPARE===';
echo '--- old elevation ---';
echo 'Tksc@197653' | sudo -S find /opt/cloudApi2_3/data/static_data/elevation -maxdepth 2 | head -15;
echo '--- new elevation before ---';
echo 'Tksc@197653' | sudo -S find /opt/cloudApi2_3.1/data/static_data/elevation -maxdepth 2 | head -15;
echo '===MIGRATE_MBTILES_TO_MOUNT===';
echo 'Tksc@197653' | sudo -S cp -a /opt/cloudApi2_3/data/static_data/asset-map/mbtiles/satellite-2017-11-01-china_shenzhen.mbtiles /var/lib/flighthub2/asset-map/mbtiles/ && echo mbtiles_copied;
echo '===MIGRATE_ELEVATION===';
echo 'Tksc@197653' | sudo -S cp -an /opt/cloudApi2_3/data/static_data/elevation/. /opt/cloudApi2_3.1/data/static_data/elevation/ 2>&1 && echo elevation_copied;
echo '--- new elevation after ---';
echo 'Tksc@197653' | sudo -S du -sh /opt/cloudApi2_3.1/data/static_data/elevation/;
echo '===RESTART_MAP_SERVICES===';
echo 'Tksc@197653' | sudo -S docker restart map-server-gl es-digital-elevation-data geo-resource 2>&1;
sleep 8;
echo '===VERIFY_TILESETS===';
echo 'Tksc@197653' | sudo -S docker exec map-server-gl sh -c 'ls /tilesets/' 2>&1;
echo '===VERIFY_PNG_30813===';
code=$(curl -s -o /dev/null -w '%{http_code}' -m 8 "http://192.168.76.141:30813/png-tiles_1/11/1690/779.jpg");
echo "png_tile_code=$code";
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
