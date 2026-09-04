#!/usr/bin/env bash
# 诊断（五）：GNSS 任务（03b476d3，UTC 01:42:42~01:43:25）时间窗内机场上报的 events/services 全量
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
W1=2026-08-25T01:42:40; W2=2026-08-25T01:43:30;
docker logs --since $W1 --until $W2 tas-service 2>&1 | grep -aE "8UUXN2K00A0144/(events|services|/requests)" > /tmp/ev.txt;
wc -l /tmp/ev.txt;
echo ===EVENTS_METHODS===;
grep -aoE "\"method\":\"[a-z_0-9]+\"" /tmp/ev.txt | sort | uniq -c;
echo ===FLIGHTTASK_RELATED===;
grep -aE "flighttask|flight_task|task_progress|cover_open|cover_close|drone_open|power_on" /tmp/ev.txt | grep -aoE "\{.*" | head -30;
echo ===RAW_SAMPLE===;
grep -a "/events" /tmp/ev.txt | tail -25 | cut -c1-600;
rm -f /tmp/ev.txt;
echo ===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
