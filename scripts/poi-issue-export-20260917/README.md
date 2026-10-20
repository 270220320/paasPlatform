# 环绕飞行（POI）失败问题日志包

## 一、问题描述

司空驾驶舱中手动控制飞行状态下，点击「环绕飞行」功能持续失败，前端报错：

```
send enter poi mode command is failed
```

**2026-09-17 15:43 ~ 15:49（UTC 07:43 ~ 07:49）** 期间共触发 5 次 `poi_mode_enter`，全部失败，失败模式完全一致（云端下发命令后 **8 秒无设备应答，等待 services_reply 超时**）。

## 二、设备与环境信息

| 项目 | 值 |
|---|---|
| 部署形态 | 司空私有化部署（FlightHub 3 / ES310） |
| 机场 SN | 7CTDM6F00BQY6R |
| 机场机型 | DJI Dock（DeviceModelKey 3-2-0） |
| 机场固件 | **14.03.0915**（thing_version 1.3.2） |
| 飞行器 SN | 1581F6Q8X252U00G05BU |
| 飞行器机型 | Matrice 4D 系列（DeviceModelKey 0-91-1），电池固件 26.03.00.51 |
| 负载 | 81-0-0（可见光） |
| 操作入口 | 司空驾驶舱（手动控制飞行模式） |

## 三、时间线（北京时间 2026-09-17）

| 时间 | 事件 |
|---|---|
| 15:35:12 | 飞行器完成起飞（command_task_cmd_executed，flight_task_type=2） |
| 15:43:02 | 飞行器手动控制飞行中：mode_code=3，相对高度 54.9m，RTK 固定解，DRC 已连接（机场 drc_state=2） |
| 15:43:33 | 第 1 次环绕：云端向 `thing/product/7CTDM6F00BQY6R/services` 下发 `poi_mode_enter`（环绕点 39.065574,117.218685，height 0.87m，距飞机水平约 20m）→ 8 秒无 reply，超时失败（code 212018） |
| 15:43:48 / 15:44:04 / 15:44:25 | 第 2~4 次重试，参数相同，同样 8 秒超时失败 |
| 15:49:32 | 第 5 次环绕：飞机已飞至环绕点附近约 37m（39.062915,117.218940，高度 81.5m，环绕点 39.062687,117.218618，height 1.16m）→ 同样 8 秒超时失败 |

## 四、关键日志证据（按查阅顺序）

1. **HTTP 请求链**（`logs/03a-pf-gateway-service_poi.log`、`logs/03b-es-management_poi.log`）：
   5 次 `POST /gateway/atc/api/v1/flight-tasks/poi-enter`，每次 8 秒后返回 HTTP 400。

2. **POI 任务处理与错误**（`logs/01-es-task_poi_full.log`，搜索 `poi_mode_enter` 或 `212018`）：
   ```
   CreateService: identifier=poi_mode_enter, sn=7CTDM6F00BQY6R,
   data={"payload_index":"81-0-0","height":1.163675,"latitude":39.062687,"longitude":117.218618}
   ...
   tas service request handled ... code:212018 duration:8.004 http_status_code:400 identifier:poi_mode_enter
   [PaasTaskController] PoiModeEnter error: send enter poi mode command is failed
   ```
   `poi_tasks` 表记录由 status=2 改为 status=4（失败）。

3. **命令下发与超时（核心）**（`logs/02-tas-service_poi_commands.log`）：
   ```
   07:43:33  send emqx msg  topic:thing/product/7CTDM6F00BQY6R/services
             method:poi_mode_enter  data:{height:0.873228, latitude:39.065574, longitude:117.218685, payload_index:81-0-0}
   07:43:41  sent_service timeout:8  topic:thing/product/7CTDM6F00BQY6R/services  method:poi_mode_enter
             → response: {"output":{},"status":"timeout"}  result.code=212018
   ```
   **窗口内无任何 `services_reply`（设备零应答，连错误码都未返回）**。

4. **设备状态快照**（`logs/05-device-osd-snapshots.log`）：
   失败前后机场 OSD 持续上报（MQTT 链路健康）、机场 `drc_state=2`（DRC 已连接）、飞行器 `mode_code=3`（手动控制/导航 API 模式）、RTK 固定解、卫星数 31+。

5. **DRC 服务背景**（`logs/04-es-drc-service_window.log`）：
   DRC 服务健康，直播通道正常（Agora），无 DRC 断链记录。

## 五、我方已排除的原因

- DRC 链路未建立（实际 drc_state=2 已连接，且手动控制飞行正常）
- MQTT 链路故障（失败期间机场 OSD 以 0.5s 级持续上报，链路健康）
- 环绕参数超限（环绕点距飞机水平 20~37m、负载索引正常；若参数被设备拒绝应返回错误码而非静默超时）
- 云端未下发命令（tas-service 日志确认 EMQX publish 成功）

## 六、疑点与协助请求

**怀疑设备端（机场固件 14.03.0915 / thing 1.3.2 或 M4D 系列飞行器固件）未实现或未启用 `poi_mode_enter` 服务命令的响应**，导致云端 8 秒等待 `services_reply` 超时（错误码 212018），前端展示 "send enter poi mode command is failed"。

请求协助确认：
1. Dock（3-2-0）+ Matrice 4D 系列（0-91-1）组合是否支持云端 `poi_mode_enter`（POI 环绕）指令？
2. 若支持，所需的最低机场/飞行器固件版本是多少？（当前机场 14.03.0915）
3. 设备端侧是否有对应固件日志可进一步定位为何收到命令不回复？

## 七、日志文件清单

| 文件 | 内容 | 说明 |
|---|---|---|
| logs/01-es-task_poi_full.log | es-task 容器全部 poi 相关日志 | POI 任务创建、错误 212018、poi_tasks 状态流转 |
| logs/02-tas-service_poi_commands.log | tas-service 容器命令下发日志 | MQTT services 下发、8 秒超时、services_reply 缺失证明 |
| logs/03a-pf-gateway-service_poi.log | 网关 poi-enter 请求 | HTTP 入口链路 |
| logs/03b-es-management_poi.log | es-management poi 相关 | HTTP/WS 辅助链路 |
| logs/04-es-drc-service_window.log | es-drc-service 时间窗日志（已滤噪） | DRC 会话背景 |
| logs/05-device-osd-snapshots.log | 设备 OSD 关键快照 | drc_state=2、mode_code=3、位置高度证据 |

> 注：所有日志时间戳均为 UTC，北京时间 = UTC + 8。
