#!/usr/bin/env bash
# 司空3.1升级（十七）：查看地图配置与瓦片数据结构
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===MAP_CONFIG_YAML===';
echo 'Tksc@197653' | sudo -S cat /var/lib/flighthub2/asset-map/map_config.yaml;
echo '===CONF_DIR===';
echo 'Tksc@197653' | sudo -S find /var/lib/flighthub2/asset-map/conf -type f 2>/dev/null | head -10;
echo '===PNG_TILES_STRUCTURE===';
echo 'Tksc@197653' | sudo -S ls /var/lib/flighthub2/asset-map/png-tiles/ | head -10;
echo 'Tksc@197653' | sudo -S find /var/lib/flighthub2/asset-map/png-tiles -maxdepth 2 -type d | head -10;
echo '===MBTILES_BOTH===';
echo '--- old 3.0 mbtiles ---';
echo 'Tksc@197653' | sudo -S ls -la /opt/cloudApi2_3/data/static_data/asset-map/mbtiles/ | head -8;
echo '--- current mbtiles ---';
echo 'Tksc@197653' | sudo -S ls -la /var/lib/flighthub2/asset-map/mbtiles/;
echo '===STREET_TILES===';
echo 'Tksc@197653' | sudo -S ls -la /var/lib/flighthub2/asset-map/street-tiles/;
echo '===STATIC_31_ASSETMAP===';
echo 'Tksc@197653' | sudo -S find /opt/cloudApi2_3.1/data/static_data/asset-map -maxdepth 2 | head -10;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
