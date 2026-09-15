#!/usr/bin/env bash
# 司空3.1升级（二十六）：补删 files 记录 + 清理孤儿 kmz 目录
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo '===FILES_ALL_ROWS===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 psql -U app_rw -d file_storage -c "SELECT id, uuid, object_id, name, file_type, deleted_at FROM files ORDER BY id" 2>&1 | head -15;
echo '===DELETE_FILE_ID1===';
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 psql -U app_rw -d file_storage -c "DELETE FROM files WHERE id=1" 2>&1;
echo '===DELETE_ORPHAN_KMZ===';
REF=$(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 psql -U app_rw -d file_storage -Atc "SELECT count(*) FROM files WHERE object_id::text LIKE '%6da41ab5%' OR uuid::text LIKE '%6da41ab5%'" 2>/dev/null);
echo "ref_count=$REF";
if [ "$REF" = "0" ]; then
  echo 'Tksc@197653' | sudo -S docker exec minio sh -c 'rm -rf /data/file-storage-privatization/6da41ab5-07e8-476d-927d-4ee21f5ff967 && echo orphan_kmz_deleted';
else
  echo "skip: still referenced";
fi;
echo '===FINAL_VERIFY===';
echo "wayline_files=$(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 psql -U app_rw -d file_storage -Atc 'SELECT count(*) FROM wayline_files' 2>/dev/null)";
echo "files_total=$(echo 'Tksc@197653' | sudo -S docker exec postgres_instance_2 psql -U app_rw -d file_storage -Atc 'SELECT count(*) FROM files' 2>/dev/null)";
echo 'Tksc@197653' | sudo -S docker exec minio sh -c 'ls /data/file-storage-privatization/' 2>/dev/null;
echo "file_storage_health=$(echo 'Tksc@197653' | sudo -S docker inspect file-storage --format '{{.State.Health.Status}}' 2>/dev/null)";
echo '===DONE==='
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
