-- 若已执行过旧版 ddl-fh2-v1.1.sql，用本脚本把 frontHost / 组件 SDK 改成无端口
USE `UAVPlatform`;

UPDATE `t_fh2_config`
SET
  `front_host` = 'http://192.168.41.185',
  `wayline_sdk_css` = 'http://192.168.41.185/paasjs-demo/wayline-edit-sdk/index.css',
  `wayline_sdk_js`  = 'http://192.168.41.185/paasjs-demo/wayline-edit-sdk/index.js',
  `device_sdk_css`  = 'http://192.168.41.185/paasjs-demo/device-manager-sdk/index.css',
  `device_sdk_js`   = 'http://192.168.41.185/paasjs-demo/device-manager-sdk/index.js',
  `server_host` = 'http://192.168.41.185:30812',
  `gateway_base` = 'http://192.168.41.185:30812/gateway',
  `wss_url` = 'ws://192.168.41.185:30812/openapi/v0.9/notification/ws/v1/notifications'
WHERE `workspace_id` = 1 AND `deleted` = 0;
