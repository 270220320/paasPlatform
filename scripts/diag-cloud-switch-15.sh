#!/usr/bin/env bash
# 云服务切换失败排查（十五）：提取完整 target_config + origin/target 逐字段对照
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
docker logs tas-service --tail 200000 2>&1 | grep -a "send emqx msg" | grep -a "cloud_environment_switch" | tail -1 > /tmp/switch_line.txt;
echo ===LINE_LENGTH===;
wc -c /tmp/switch_line.txt;
echo ===TARGET_CONFIG_EXTRACT===;
grep -o "target_config.*switch_callback" /tmp/switch_line.txt | head -c 2000; echo;
echo ===FULL_BODY_PART1===;
cut -c1-1600 /tmp/switch_line.txt;
echo ===FULL_BODY_PART2===;
cut -c1601-3200 /tmp/switch_line.txt;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
