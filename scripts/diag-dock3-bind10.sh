#!/usr/bin/env bash
# 诊断第十阶段：PG pf_device_service.devices 最终状态确认
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S docker exec postgres_instance_1 psql -U app_rw -d pf_device_service -c "select sn, device_bind_status, device_online_status, active_prj_uuid, created_at, updated_at from devices where sn in ('8UUXN2K00A0144','1581F8HGX252500A009X')" 2>&1
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
