#!/usr/bin/env bash
# 司空3.1升级（三十）：扫 mysql/pg2/timescaledb 全库找12条飞行计划
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
MPASS=$(echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 printenv MYSQL_ROOT_PASSWORD 2>/dev/null);
echo '===MYSQL_ROWCOUNTS===';
for db in ApolloConfigDB ApolloPortalDB ThingAccessService es310_management_service es_duplex_service es_open_service live_service pf_device_service pf_live_service; do
  R2=$(echo 'Tksc@197653' | sudo -S docker exec mysql_instance_1 sh -c "mysql -uroot -p'$MPASS' -N -e \"SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema='$db' AND table_rows > 0 ORDER BY table_rows DESC LIMIT 8\"" 2>/dev/null);
  [ -n "$R2" ] && echo "--- mysql/$db ---" && echo "$R2";
done;
echo '===PG2_ROWCOUNTS===';
for db in file_storage digital_elevation_data_service es_flysafe esds esws es_reconstruction_service pf_terra_manage_service geo_resource; do
  R3=$(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 psql -U app_rw -d $db -Atc "SELECT relname, n_live_tup FROM pg_stat_user_tables WHERE n_live_tup > 0 ORDER BY n_live_tup DESC LIMIT 8" 2>/dev/null);
  [ -n "$R3" ] && echo "--- pg2/$db ---" && echo "$R3";
done;
echo '===TSDB_ENV===';
echo 'Tksc@197653' | sudo -S docker exec timescaledb env 2>/dev/null | grep -E 'POSTGRES_(USER|PASSWORD|DB)';
echo '===TSDB_ROWCOUNTS===';
TSU=$(echo 'Tksc@197653' | sudo -S docker exec timescaledb printenv POSTGRES_USER 2>/dev/null);
for db in $(echo 'Tksc@197653' | sudo -S docker exec timescaledb psql -U ${TSU:-postgres} -d postgres -Atc "SELECT datname FROM pg_database WHERE NOT datistemplate" 2>/dev/null); do
  R4=$(echo 'Tksc@197653' | sudo -S docker exec timescaledb psql -U ${TSU:-postgres} -d $db -Atc "SELECT relname, n_live_tup FROM pg_stat_user_tables WHERE n_live_tup > 0 ORDER BY n_live_tup DESC LIMIT 8" 2>/dev/null);
  [ -n "$R4" ] && echo "--- tsdb/$db ---" && echo "$R4";
done;
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
