#!/usr/bin/env bash
# 诊断第六阶段：PostgreSQL 设备主表绑定状态
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
echo ====PG1_DBS====;
docker exec postgres_instance_1 psql -U postgres -Atc "select datname from pg_database" 2>&1 | head;
echo ====PG2_DBS====;
docker exec postgres_instance_2 psql -U postgres -Atc "select datname from pg_database" 2>&1 | head;
echo ====TS_DBS====;
docker exec timescaledb psql -U postgres -Atc "select datname from pg_database" 2>&1 | head;
for pg in postgres_instance_1 postgres_instance_2; do for db in $(docker exec $pg psql -U postgres -Atc "select datname from pg_database" 2>/dev/null | grep -viE "template|postgres$"); do HAS=$(docker exec $pg psql -U postgres -d $db -Atc "select count(*) from information_schema.tables where table_name=\x27devices\x27" 2>/dev/null); [ "$HAS" != "0" ] && [ -n "$HAS" ] && echo "====[$pg/$db devices]====" && docker exec $pg psql -U postgres -d $db -c "select sn,device_bind_status,device_online_status,active_prj_uuid from devices where sn in (\x278UUXN2K00A0144\x27,\x271581F8HGX252500A009X\x27)" 2>&1; done; done'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
