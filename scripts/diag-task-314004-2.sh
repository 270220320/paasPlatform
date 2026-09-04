#!/usr/bin/env bash
# 诊断 314004（二）：任务时间窗内机场/飞机 OSD 状态序列（盖、模式、RTK、在舱、充电）
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
W1=2026-08-25T01:33:40; W2=2026-08-25T01:34:40;
docker logs --since $W1 --until $W2 tas-service 2>&1 | grep -a thing/product > /tmp/w.txt;
echo ===DOCK_OSD_SEQ===;
grep -a 8UUXN2K00A0144/osd /tmp/w.txt | while read l; do
  t=$(echo "$l" | grep -aoE "timestamp.:[0-9]+" | head -1 | cut -d: -f2);
  c=$(echo "$l" | grep -aoE "cover_state.:[0-9]+" | head -1 | cut -d: -f2);
  m=$(echo "$l" | grep -aoE "mode_code.:[0-9]+" | head -1 | cut -d: -f2);
  d=$(echo "$l" | grep -aoE "drone_in_dock.:[0-9]+" | head -1 | cut -d: -f2);
  ch=$(echo "$l" | grep -aoE "capacity_percent.:[0-9]+" | head -1 | cut -d: -f2);
  fi_=$(echo "$l" | grep -aoE "is_fixed.:[0-9]+" | head -1 | cut -d: -f2);
  rt=$(echo "$l" | grep -aoE "rtk_number.:[0-9]+" | head -1 | cut -d: -f2);
  echo "ts=$t cover=$c mode=$m inDock=$d batt=$ch rtkFixed=$fi_ rtkNum=$rt";
done | sort -u;
echo ===DRONE_OSD_SEQ===;
grep -a 1581F8HGX252500A009X/osd /tmp/w.txt | while read l; do
  t=$(echo "$l" | grep -aoE "timestamp.:[0-9]+" | head -1 | cut -d: -f2);
  m=$(echo "$l" | grep -aoE "mode_code.:[0-9]+" | head -1 | cut -d: -f2);
  fi_=$(echo "$l" | grep -aoE "is_fixed.:[0-9]+" | head -1 | cut -d: -f2);
  rt=$(echo "$l" | grep -aoE "rtk_number.:[0-9]+" | head -1 | cut -d: -f2);
  gp=$(echo "$l" | grep -aoE "gps_number.:[0-9]+" | head -1 | cut -d: -f2);
  na=$(echo "$l" | grep -aoE "is_near_area_limit.:[0-9]+" | head -1 | cut -d: -f2);
  echo "ts=$t mode=$m rtkFixed=$fi_ rtkNum=$rt gps=$gp nearLimit=$na";
done | sort -u;
rm -f /tmp/w.txt'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
