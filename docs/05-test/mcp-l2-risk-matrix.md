# FlightHub MCP Server 工具风险矩阵（L2 启用评审用）

- 数据来源：2026-09-01 实测 `tools/list` + dry_run 准入实测（服务端 dji-cloud-api2 1.0.0 / SDK 3.1.0.0 / 镜像 pf-devkit-mcp:3836486）
- 等级口径：L0/L1/L2/L3 对齐官方文档「SecurityLevel 分级」；官方将快照类归 L1（“取一次快照计为 L1”），本矩阵从其口径
- 字段可信度分级：
  - **[Schema]** = 直接派生自 tools/list 的 inputSchema（机器可验证：required 数组 / properties 键）
  - **[运营]** = 部署运营方语义标注（riskLevel / readback / cleanup / idempotent）
- 服务端当前配置：`FH_MAX_SECURITY_LEVEL=2`（L3 类操作被服务端拒绝）

## 全局约定

**风险等级定义**

| 等级 | 含义 |
|---|---|
| L0 | 纯查询，无副作用 |
| L1 | 平台/设备管理操作（物理动作、数据写入/删除），不直接引起飞行 |
| L2 | 飞行相关（起飞、飞控、航线任务、空中载荷操作） |
| L3 | 紧急/不可逆安全操作（当前配置下禁用） |
| 服务级 | 影响 MCP 服务可用性（fh_ops:shutdown） |

**confirm 语义（两层）**
- `required=true`：confirm 在 inputSchema.required 中，缺失即被 Schema 校验拒绝 [Schema]
- `描述要求=true`：confirm 在 Schema 中但非 required，工具 description 明确"写操作需 confirm=true"，服务端运行时校验 [Schema+运营]

**统一响应包装（responseSchema）**

外层 JSON-RPC result：
```json
{"content":[{"type":"text","text":"<内层JSON字符串>"}],"isError":false}
```
内层 text（JSON 字符串）统一结构：
```json
{"success": true|false, "data": {…}, "message": "…", "next_steps": [ … ]}
```
**已知限制（如实声明）**：`data` 为动态对象，服务端未发布逐工具静态 Schema。补偿方案：所有支持 dry_run 的 L2 工具可先以 `dry_run=true` 调用获取真实响应样例，据此固化本地校验规则。

**错误码分层（failureCodes，全局适用）**

| 层 | 表现 | 说明 |
|---|---|---|
| 鉴权 | HTTP 401 + JSON-RPC `-32401` | 实测确认，GET /sse 与 POST /message 均适用 |
| 协议 | JSON-RPC `-32601`（方法不存在，实测）；`-32602`（参数无效） | 标准保留码 |
| MCP 业务失败 | HTTP 200，内层 `{"success":false,"message":"…"}` 且 isError=false | 不走 JSON-RPC error，必须解析内层 JSON |
| MCP 前置拒绝 | HTTP 200，内层 `{"success":false,"error_message":"…","suggestion":"…"}` 且 isError=true | 等级不足/前置条件失败走此通道；实测：emergency_stop 返回 "This operation requires L3, but session allows max L2" |
| SDK/设备层 | 内层 success:false，message 含 DJI 错误码（如 `325003`） | 按 DJI Cloud API 错误码规范透传，无对外静态映射表；逐工具错误码取决于设备型号/状态，无法预先穷举 |

**幂等性全局说明**：查询类天然幂等；写操作语义非幂等（重复 create = 产生新任务）。服务端开启指令幂等缓存（300s TTL，仅对相同指令的**重试**去重，不是 API 级幂等）。标注“近似幂等”的资源获取类（acquire）重复调用预期返回已持有，未实测验证。

**dry_run 实测语义与准入行为（2026-09-01，FH_MAX_SECURITY_LEVEL=2，设备离线状态）**
- fh_dock_control(cover_open) dry_run：返回完整预览（would_execute + prerequisites_checked + security_level:READ_ONLY）。注意 security_level=READ_ONLY 是 dry_run 调用的降级显示，不能据此推断工具本身等级
- fh_takeoff / fh_flyto dry_run：未被等级拦截，进入 DRC 前置检查，设备离线时报 "Failed to connect DRC (ERROR_OPERATION_FAILED)"。即这两类工具的 dry_run 会实际尝试 DRC 建链——与官方文档 9.3 “不会实际调用 SDK”的表述不一致，以实测为准
- fh_emergency_stop dry_run：等级检查先于 dry_run，直接拒绝（requires L3）
- 结论：dry_run 对不同工具的“深度”不一致，接入方不应假设 dry_run 永远零副作用；DRC 类工具的 dry_run 依赖设备在线才能返回完整预览

## 风险矩阵（53 工具）

| tool | action 分档 | riskLevel | requiresConfirm | dryRun | idempotent | readbackTool | cleanupRequirement |
|---|---|---|---|---|---|---|---|
| fh_connect | — | L0 | 否 | 否 | 是 | fh_session_info | 无；注意：全局单 SDK 会话，重连会重置会话影响所有客户端 |
| fh_session_info | — | L0 | 否 | 否 | 是 | 自身 | 无 |
| fh_ops | status / set_log_level / shutdown | L0 / L0 / 服务级 | 否 | 否 | status=是；shutdown=否 | fh_ops(status) | shutdown 后需容器重启恢复 |
| fh_device_list | — | L0 | 否 | 否 | 是 | 自身 | 无 |
| fh_device_recommend | — | L0 | 否 | 否 | 是 | fh_device_list | 无 |
| fh_wayline_recommend | — | L0 | 否 | 否 | 是 | fh_workspace(list) | 无 |
| fh_wayline_status | — | L0 | 否 | 否 | 是 | 自身 | 无 |
| fh_dock_snapshot | — | L1（官方口径：快照） | 否 | 否 | 是 | 自身 | 无 |
| fh_drone_snapshot | — | L1（官方口径：快照） | 否 | 否 | 是 | 自身 | 无 |
| fh_osd_snapshot | — | L1（官方口径：快照） | 否 | 否 | 是 | 自身 | 无 |
| fh_media_list | — | L0 | 否 | 否 | 是 | 自身 | 无 |
| fh_media_download | — | L0 | 否 | 否 | 是（URL 1-24h 有效） | fh_media_list | 无 |
| fh_hms | query_history | L0 | 否 | 否 | 是 | 自身 | 无 |
| fh_flight_area_list | — | L0 | 否 | 否 | 是 | 自身 | 无 |
| fh_flight_record | export / get_progress | L0 | 否 | 否 | get_progress=是；export 重发=新任务 | get_progress | 无（导出任务自动完成） |
| fh_maintenance | 读3项 / create_order | L0 / L1 | 否 | 否 | 读=是；create=否 | get_detail / list_orders | 无 |
| fh_flysafe | check / upgrade | L0 / L1 | upgrade=描述要求 | 否 | check=是；upgrade=否 | fh_flysafe(check) | 无 |
| fh_tcas | get / update | L0 / L1 | update=描述要求 | 否 | get=是；update=同参数重设近似幂等 | fh_tcas(get) | 无 |
| fh_unlock_license | list / submit,upload,switch,check,clear | L0 / L1 | 写操作=描述要求；clear 为不可逆高危 | 是 | list=是；clear=不可逆 | fh_unlock_license(list) | 无 |
| fh_diagnosis | create / get_report,list_reports / operate | L1 / L0 / L1 | 否（Schema 无） | 否 | 读=是；create=否 | get_report / list_reports | 进行中任务可 operate(cancel) |
| fh_firmware | 读5项(check_status,list,query_progress,release_note,compatibility) / 写6项(upgrade,upload,submit,release,unrelease,delete) | L0 / L1 | 写操作=描述要求 | 否 | 读=是；upgrade=否 | query_progress / check_status | 无 |
| fh_log | query,create_report,list_reports / operate,upload | L0 / L1 | delete=描述要求 | 否 | 读=是；create_report=否 | list_reports | 无 |
| fh_device_manage | detail,hms_current / rename / unbind | L0 / L1 / L1-高危 | rename,unbind=描述要求 | 是 | 读=是；写=否 | fh_device_list / detail | unbind 后需在司空平台重新绑定恢复 |
| fh_device_debug | set_* 配置31项中占14项 / 物理(open,close,reboot,switch等) / format_device,format_drone | L1 / L1 / L1-不可逆 | 物理操作=描述要求（set_* 不需要） | 否 | set_*=同参数重设；物理=否；format=不可逆 | fh_dock_snapshot / fh_device_list | reboot 后等设备重新上线；format 不可恢复 |
| fh_dock_control | cover_open,cover_close,charge_start,charge_stop,light_on,light_off | L1 | **required=true** | 是 | 否（状态切换） | fh_dock_snapshot | 开舱盖后不飞需手动关 |
| fh_media_record | start,stop / list | L1 / L0 | start,stop=描述要求 | 否 | list=是；开关近似幂等 | list | start 后必须 stop（存储/计费） |
| fh_psdk_widget | set_value,set_input_text | L1 | 否 | 否 | 是（同值重设） | 无直接回读 | 无 |
| fh_speaker | 全部9项(audio_play,tts_play,stop,replay,realtime_start,realtime_stop,set_volume,set_mode,tts_config) | L1 | 全部=描述要求 | 是 | 否（重复=重复播放） | 无直接回读 | realtime_start 后必须 realtime_stop |
| fh_searchlight | 全部6项(calibration,fine_tuning,gimbal_control,mode_set,type_set,wide_field_mode) | L1（空中使用时伴随 L2 状态） | 描述要求 | 否 | mode_set=是 | 无直接回读 | mode_set(mode=0) 关灯 |
| fh_offline_map | 读3项(get_model_info,get_sync_status,search_signal_maps) / 写(update_model_info,set_enabled,sync) / 破坏性(cancel,invoke_flysafe_upgrade) | L0 / L1 / L1 | cancel,invoke_flysafe_upgrade=描述要求 | 否 | 读=是；sync=可重推 | get_sync_status | 无 |
| fh_flight_area_manage | create,update,delete | L1（结果影响设备限飞执法） | **required=true** | 是 | 否 | fh_flight_area_list | delete 前记录原配置以便恢复 |
| fh_flight_area_sync | sync / status | L1 / L0 | sync=描述要求 | 是 | sync=可重推（幂等语义） | status | 无 |
| fh_flight_task | list,detail / create,edit,delete / execute / update_status | L0 / L1 / **L2** / L1 | 写操作=描述要求 | 否 | 读=是；execute=否 | fh_wayline_status / detail | execute 后跟踪至终态；中止用 fh_wayline_control(cancel) |
| fh_wayline_upload | 上传航线并建任务 | L2 | **required=true** | 否 | 否（重复=多任务） | fh_flight_task(list) | 未执行任务用 fh_flight_task(delete) 清理 |
| fh_wayline_execute | 执行已建任务（真机自主飞行） | **L2** | **required=true** | 是 | 否 | fh_wayline_status | 跟踪至终态；异常用 fh_wayline_control(cancel) 或 fh_return_home |
| fh_wayline_control | pause,resume,cancel | L2 | **required=true** | 是 | 状态切换近似幂等 | fh_wayline_status | cancel=任务终止+自动 RTH |
| fh_workspace | list,detail,batch_download / submit,update,duplicate / batch_delete | L0 / L1 / L1-不可逆 | batch_delete=描述要求 | 否 | 读=是；写=否 | list | 无 |
| fh_terra | 读4项(status,model_list,model_url,quality_report) / create_reconstruction,create_panorama / delete_model / retry | L0 / L1（算力任务） / L1-不可逆 | create_*,delete_model=描述要求 | 否 | 读=是；create=否 | status | 失败任务可 retry；无强制清理 |
| fh_control_acquire | 获取飞控权 | L2 | 否（资源原语） | 否 | 近似幂等（未实测） | fh_session_info(control_authority) | **强制**：fh_control_release 配对释放，含失败路径 |
| fh_control_release | 释放飞控权 | L2 | 否 | 否 | 是 | fh_session_info | 本身即清理动作 |
| fh_payload_acquire | 获取载荷控制权 | L2 | 否 | 否 | 近似幂等（未实测） | fh_camera(get_camera_state) | **强制**：fh_payload_release 配对释放 |
| fh_payload_release | 释放载荷控制权 | L2 | 否 | 否 | 是 | 同上 | 本身即清理动作 |
| fh_drc_connect | 建立 DRC 低时延链路 | L2 | 否 | 否 | 近似幂等（未实测） | fh_session_info(drc_link) | **强制**：fh_drc_disconnect 后再 release |
| fh_drc_disconnect | 断开 DRC | L2 | 否 | 否 | 是 | 同上 | 本身即清理动作 |
| fh_takeoff | 起飞（悬停待命） | **L2**（实测：=2 下不被等级拦截；官方文档 9.1 曾示例归 L3，以实测为准） | **required=true** | 是 | 否 | fh_osd_snapshot | 起飞后必须有终态动作（任务或 return_home）；结束后 drc_disconnect + control_release。实测注：dry_run 仍实际尝试 DRC 建链，设备离线时报 ERROR_OPERATION_FAILED 而非返回参数预览 |
| fh_flyto | start,update / stop / progress | **L2** / L2 / L0 | **required=true** | 是 | 否 | progress / fh_osd_snapshot | start 后 stop 或到达；飞行链由 return_home 收尾。实测注：dry_run 同样尝试 DRC 建链（同 takeoff） |
| fh_poi | start / stop / status | **L2** / L2 / L0 | **required=true** | 是 | 否 | status / fh_osd_snapshot | stop 后 return_home 收尾 |
| fh_stick_move | 8方向短时移动(100-5000ms 自动结束) | **L2** | **required=true** | 是 | 否（重复=持续位移） | fh_osd_snapshot | 无（自动结束） |
| fh_return_home | start / cancel | **L2** | **required=true** | 是 | 否 | fh_osd_snapshot | 落地后 drc_disconnect + control_release |
| fh_live_start | start,stop,quality,switch_source | L1（飞行中使用时伴随 L2） | **required=true** | 否 | 否 | 无直接回读 | start 后必须 stop（流资源） |
| fh_camera | 查询5项(get_camera_state,get_gimbal_state,get_lens_state) / 空中载荷操作14项(photo,video,zoom,gimbal,panorama等) | L0 / **L2**（空中操作） | 否（Schema 无 confirm，靠前置权限链约束） | 否 | 查询=是；拍摄=否 | get_camera_state / get_gimbal_state | video_start→video_stop；panorama_start→panorama_stop；航线任务执行中固件锁定相机 |
| fh_media_delete | 删除媒体文件 | L1-不可逆 | **required=true** | 是 | 否（不可恢复） | fh_media_list | 无（不可恢复） |
| fh_emergency_stop | 急停/强制降落 | **L3** | **required=true** + reason 必填 | 是 | 否 | fh_osd_snapshot | 电机停转后可能需现场恢复 | **实测确认：=2 下被等级拦截，返回 "requires L3, but session allows max L2"，dry_run 亦拦截** |

## L2 执行链强制顺序（运营方执行规范）

```
fh_control_acquire → fh_drc_connect → [fh_takeoff | fh_wayline_execute | fh_flyto]
    → (载荷操作前 fh_payload_acquire) → 终态: fh_return_home
    → fh_drc_disconnect → fh_control_release
异常任意点: fh_return_home(兜底) 或 fh_emergency_stop(仅 L3 授权时)
监控: fh_osd_snapshot / fh_wayline_status / fh_session_info
```

## 审计与治理

- 审计日志：容器级开启（json-file 500M×5），fh_emergency_stop 强制 reason 留痕
- 限流：100 req / 60s / IP；会话超时 1800s
- 飞控权全局互斥：fh_control_acquire 为排他资源，同一时刻仅一个操作者
- 单一静态 Token，无租户隔离：Token 持有方权限等同，撤销=轮换
