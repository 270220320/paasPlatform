#!/usr/bin/env bash
# 诊断（十）：定位 FlexNet/DTCP 改写组件（traefik 插件或独立容器）及其配置
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ===TRAEFIK_MOUNTS===;
docker inspect traefik --format "{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}";
echo ===GREP_FLEXNET_IN_TRAEFIK_CONF===;
for D in $(docker inspect traefik --format "{{range .Mounts}}{{.Source}} {{end}}"); do
  [ -f "$D" ] && grep -laiE "flexnet|dtcp|resp.*diff|body.*replace|rewrite" "$D" 2>/dev/null;
  [ -d "$D" ] && grep -rlaiE "flexnet|dtcp" "$D" 2>/dev/null | head -5;
done;
echo ===TRAEFIK_CMD===;
docker inspect traefik --format "{{json .Config.Cmd}} {{json .Config.Entrypoint}}";
echo ===CONTAINERS_FLEXNET===;
docker ps -a --format "{{.Names}} {{.Image}}" | grep -aiE "flex|dtcp|proxy";
echo ===GREP_FLEXNET_ALL_CONF===;
grep -rlaiE "flexnet" /data 2>/dev/null | head -10;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
