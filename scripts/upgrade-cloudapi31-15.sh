#!/usr/bin/env bash
# 司空3.1升级（十五）：定位离线地图数据 —— 对比新旧 data 目录 + map-server 挂载
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===OLD_DATA_DIR===';
echo 'Tksc@197653' | sudo -S du -sh /opt/cloudApi2_3/data/* 2>/dev/null | sort -rh | head -15;
echo '===NEW_DATA_DIR===';
echo 'Tksc@197653' | sudo -S du -sh /opt/cloudApi2_3.1/data/* 2>/dev/null | sort -rh | head -15;
echo '===MAP_MOUNTS_31===';
for c in map-server-nginx map-server-gl es-digital-elevation-data; do
  echo "--- $c ---";
  echo 'Tksc@197653' | sudo -S docker inspect $c --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{"\n"}}{{end}}' 2>/dev/null;
done;
echo '===OLD_MAP_DATA_FIND===';
echo 'Tksc@197653' | sudo -S find /opt/cloudApi2_3/data -maxdepth 3 -type d \( -iname '*tile*' -o -iname '*map*' \) 2>/dev/null | head -15;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
