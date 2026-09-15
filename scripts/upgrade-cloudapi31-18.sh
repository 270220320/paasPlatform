#!/usr/bin/env bash
# 司空3.1升级（十八）：实测 30813 瓦片服务可达性 + traefik 入口检查
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===FIND_SAMPLE_TILE===';
SAMPLE=$(echo 'Tksc@197653' | sudo -S find /var/lib/flighthub2/asset-map/png-tiles/png-tiles_1 -name '*.jpg' 2>/dev/null | head -1);
echo "sample=$SAMPLE";
REL=${SAMPLE#/var/lib/flighthub2/asset-map/png-tiles/};
echo "rel=$REL";
echo '===CURL_30813===';
code=$(curl -s -o /dev/null -w '%{http_code}' -m 8 "http://192.168.76.141:30813/$REL");
echo "http_code=$code";
code2=$(curl -s -o /dev/null -w '%{http_code}' -m 8 "http://127.0.0.1:30813/$REL");
echo "local_code=$code2";
echo '===PORT_30813_LISTEN===';
echo 'Tksc@197653' | sudo -S ss -tlnp 2>/dev/null | grep 30813 || echo "30813_NOT_LISTENING";
echo '===TRAEFIK_ENTRYPOINTS===';
echo 'Tksc@197653' | sudo -S docker exec traefik wget -qO- http://127.0.0.1:8080/api/rawdata 2>/dev/null | grep -o '"map-custom[^,]*' | head -3;
echo '===MAP_NGINX_DIRECT===';
echo 'Tksc@197653' | sudo -S docker exec map-server-nginx sh -c 'ls /var/png-tiles/ 2>&1; wget -q -S -O /dev/null http://127.0.0.1:80/png-tiles_1/ 2>&1 | head -5' 2>&1;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
