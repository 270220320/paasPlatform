#!/usr/bin/env bash
# 二代机场76.121第四轮：定位tas-emqx-service密码校验数据源(env/挂载/配置/数据库)
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S -v 2>/dev/null
sudo bash <<'SUEOF'
echo "[START] $(date -u +%FT%T)"
echo "===[1] tas-emqx-service env==="
docker inspect tas-emqx-service --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -viE 'PATH=|LANG=|LC_|HOSTNAME|HOME=' | head -25
echo "===[2] tas-emqx-service mounts==="
docker inspect tas-emqx-service --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}' | head -15
echo "===[3] grep pwd in mounted conf==="
for f in $(docker inspect tas-emqx-service --format '{{range .Mounts}}{{println .Source}}{{end}}' | grep -v '^\s*$'); do
  [ -f "$f" ] && grep -niE 'pwd|password|secret|appid|app_key' "$f" | head -5
done
echo "===[4] emqx authn chain (connhook url)==="
docker inspect emqx --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -iE 'auth|hook|tas' | head -10
echo "===[5] mysql tas dbs==="
docker exec mysql_instance_1 sh -c 'mysql -uroot -p$(printenv MYSQL_ROOT_PASSWORD) -N -e "show databases"' 2>/dev/null | head -15
echo "===[6] postgres dbs==="
docker exec postgres_instance_1 sh -c 'psql -U postgres -At -c "select datname from pg_database"' 2>/dev/null | head -15
echo "[DONE] $(date -u +%FT%T)"
SUEOF
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
