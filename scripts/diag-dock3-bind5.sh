#!/usr/bin/env bash
# 诊断第五阶段：正确库名查询设备绑定状态/组织/绑定码
cp /mnt/d/AiFlow/scripts/fh_askpass.sh /tmp/fh_askpass.sh
chmod +x /tmp/fh_askpass.sh
export SSH_ASKPASS=/tmp/fh_askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0
R=$(base64 -w0 <<'EOF'
echo 'Tksc@197653' | sudo -S bash -c '
M="docker exec mysql_instance_1 mysql -uroot -pfzNi6gnMEM98z3VwmeLh0QHz";
echo ====PF_DEVICE_DEVICES====;
$M -e "select sn,device_bind_status,device_online_status,active_prj_uuid,device_model_key from pf_device_service.devices where sn in (\x278UUXN2K00A0144\x27,\x271581F8HGX252500A009X\x27)" 2>/dev/null;
echo ====TAS_APP_BINDINGS====;
$M -e "select * from ThingAccessService.device_app_bindings\\G" 2>/dev/null | head -40;
echo ====ES310_ORG_TABLES====;
$M -N -e "show tables from es310_management_service like \x27%org%\x27" 2>/dev/null;
echo ====ES310_DEVICES====;
$M -e "select sn,device_bind_status,device_online_status,active_prj_uuid from es310_management_service.devices where sn in (\x278UUXN2K00A0144\x27,\x271581F8HGX252500A009X\x27)" 2>/dev/null;
echo ====ORG_INFO====;
$M -e "select * from es310_management_service.organizations limit 5\\G" 2>/dev/null | head -40'
EOF
)
setsid -w ssh -o StrictHostKeyChecking=no -o ConnectTimeout=8 test@192.168.76.141 "echo $R | base64 -d | bash" 2>&1
