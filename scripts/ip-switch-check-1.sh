#!/usr/bin/env bash
# 2026-09-21 IP 切换检查：76.141 -> 76.129（同一台机器改 IP）
# 覆盖：网关/瓦片连通性、License、map_config、webhook、司空配置残留扫描
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh
export SSH_ASKPASS_REQUIRE=force
export DISPLAY=:0
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.129 'bash -s' <<'REMOTE' 2>&1
echo "===== 1. TRAEFIK 30812 gateway ====="
curl -sS -o /dev/null -w "HTTP %{http_code}\n" --connect-timeout 5 http://192.168.76.129:30812/ || echo "FAIL: 30812 unreachable"
echo "===== 2. MAP TILES 30813 ====="
for p in "png-tiles_1/3/6/3.jpg" "png-tiles_1/4/12/6.jpg"; do
  code=$(curl -sS -o /dev/null -w "%{http_code}" --connect-timeout 5 "http://192.168.76.129:30813/$p" 2>/dev/null)
  echo "$p -> HTTP ${code:-FAIL}"
done
echo "===== 3. LICENSE cmu -l ====="
/usr/bin/cmu -l 2>/dev/null | sed -n '1,25p'
echo "===== 4. map_config.yaml location & old-IP check ====="
echo "Tksc@197653" | sudo -S find /var/lib/flighthub2 /opt/cloudApi2_3.1 /opt/cloudApi2_3 -maxdepth 6 -name "map_config*.yaml" 2>/dev/null
for f in $(echo "Tksc@197653" | sudo -S find /var/lib/flighthub2 /opt/cloudApi2_3.1 /opt/cloudApi2_3 -maxdepth 6 -name "map_config*.yaml" 2>/dev/null); do
  echo "--- $f ---"
  echo "Tksc@197653" | sudo -S cat "$f" 2>/dev/null | head -40
done
echo "===== 5. WEBHOOK config ====="
echo "Tksc@197653" | sudo -S grep -rniE "webhook" /opt/cloudApi2_3.1/install/ 2>/dev/null | grep -vE "\.log|Binary" | head -15
echo "===== 6. OLD IP 76.141 residue in sikong configs ====="
echo "Tksc@197653" | sudo -S grep -rn "76\.141" /opt/cloudApi2_3.1/install/ 2>/dev/null | grep -vE "\.log|Binary file|/data/" | head -30
echo "===== CHECK DONE ====="
REMOTE
