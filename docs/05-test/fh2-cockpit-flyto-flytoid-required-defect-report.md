# 缺陷报告：PaaS 驾驶舱"飞向此处"报 `UpdateFlyToPointRequest.FlyToId required`（code 200614）

- **提交对象**：DJI 司空 2 私有化 / FlightHub PaaS 技术支持
- **报告日期**：2026-09-03
- **缺陷等级**：P1（驾驶舱核心功能"飞向此处"在特定状态组合下不可用）
- **报告方**：ISV 集成方（AiFlow 平台，PaaS SDK 嵌入驾驶舱）

---

## 1. 环境信息

| 项 | 值 |
|---|---|
| 司空私有化服务器 | 192.168.76.141（Traefik 网关 30812） |
| 司空版本代号 | es310 私有化（容器：es-task、pf-control-core、pf-atc-service、pf-device-service 等） |
| 机场 | DJI Dock 3，SN `8UUXN2K00A0144`，固件 17.02.0521 |
| 飞行器 | Matrice 4TD，SN `1581F8HGX252500A009X` |
| 前端 SDK | paas.js → paas-core.8a2a35b581aa955a.js；驾驶舱微前端 project-app/index.45879d02.js |
| 集成方式 | ISV 前端通过 `FH2.loadCockpit()` 嵌入驾驶舱，`initConfig.serverUrl` 指向司空网关；PaaS 用户（user_id 鉴权，本例 `paas_test` / PaaSUser） |
| DRC 数据通道 | mqtt_broker（`/control/api/v1/manual/data-channels` 打开成功） |

## 2. 问题描述

在 PaaS 驾驶舱中使用"飞向此处"：

1. 机场舱内状态下第一次点击"飞向此处"→ **功能正常**：飞机起飞并飞抵目标点悬停（`fly_to_point_progress_wayline_ok`）。
2. flyto 完成、飞机悬停待命期间（飞行会话仍为 flyto 型进行中），**刷新页面或重新进入驾驶舱后**再次点击"飞向此处"→ 接口报错：
   ```
   code: 200614
   message: Key: 'UpdateFlyToPointRequest.FlyToId' Error:Field validation for 'FlyToId' failed on the 'required' tag
   ```
3. 报错后每次点击均复现，"飞向此处"持续不可用，直至一键返航/降落结束飞行会话后恢复。

## 3. 复现步骤

1. 驾驶舱中选择在线机场，待飞机在舱内（inDock）；
2. 点击"飞向此处"选择目标点并确认 → 飞机起飞飞抵目标点悬停（正常）；
3. **刷新浏览器页面（或退出后重新进入驾驶舱页）**；
4. 飞机悬停期间再次点击"飞向此处"并确认 → 浏览器 Network 可见：
   `PUT /gateway/atc/api/v1/flight-tasks/fly-to-points`，请求体 `fly_to_id: ""` → 返回 200614。

## 4. 关键日志证据（司空 es-task 容器，UTC 时间 2026-09-03）

### 4.1 创建成功且响应含 fly_to_id（06:03:41）

```
POST /atc/api/v1/flight-tasks/command
params: { takeoff_mode: Takeoff, target_latitude: 39.06575, target_longitude: 117.21858, ... }
response: { code: 0, data: { flight_id: "6f771fbd-ef30-40be-8fa1-6ac633b3e60f",
                            fly_to_id:  "ee44153d-391b-405e-bc56-85c578ea2b7b" } }
```

### 4.2 flyto 执行完成（06:03:50 → 06:04:07）

```
HandleRelayFlyToProgress: status=task_ready → wayline_progress → wayline_ok (result=0)
[PaaS] ws PFNotify success, topic: topic_atc_flyto_task_event   ← PaaS 事件推送链路正常
```

### 4.3 flyto 完成后 in-flight 响应：fly_to_task 被清空，但飞行会话仍在进行（06:04:58）

```
GET /atc/api/v1/flight-tasks/in-flight?sn=8UUXN2K00A0144
response.data = {
  "flight_id": "6f771fbd-...",
  "flight_task_data": { "status": 1,                       ← 飞行会话仍进行中（flyto 型）
                        "takeoff_task_uuid": "ee44153d-391b-405e-bc56-85c578ea2b7b",   ← 仍存在
                        "takeoff_mode": "Takeoff", ... },
  "flight_type": 2,
  "fly_to_task": { "uuid": "", "status": 0, "way_points": [], ... },   ← 已被服务端清空
  "is_first_fly_to": false
}
```

> 注：`fly_to_id` 与 `flight_task_data.takeoff_task_uuid` 为同值（ee44153d-…）。

### 4.4 报错请求（06:07:49 起，多次）

```
PUT /atc/api/v1/flight-tasks/fly-to-points
referer: http://localhost:5173/cockpit/detail?gatewaySn=8UUXN2K00A0144&droneSn=1581F8HGX252500A009X
params: { fly_to_id: "", max_speed: 14,
          points: [{ height: 30.798..., latitude: 39.0659..., longitude: 117.2181... }],
          speed_mode: 0, start_point: { height: 54.75, latitude: 39.06575, longitude: 117.218584 } }
response: { code: 200614, message: "Key: 'UpdateFlyToPointRequest.FlyToId' Error:Field validation for 'FlyToId' failed on the 'required' tag" }
```

### 4.5 附带发现：es-duplex 服务缺失

es-task / pf-control-core 日志中大量出现：

```
ws notify error: Post "http://es-duplex:8080/duplex/inner/api/notify":
dial tcp: lookup es-duplex on 127.0.0.11:53: server misbehaving
（biz code: fly_to_point_progress / cloud_control_status_v2 等）
```

docker ps 中无 es-duplex 容器 —— 该私有化部署疑似缺失 es-duplex 服务，原生双工 WS 通知通道不可用（PaaS 通道 `topic_atc_flyto_task_event` 的 PFNotify 正常）。可能与前端状态失步相关，请一并核实是否为部署清单缺项。

## 5. 前端逆向分析（project-app/index.45879d02.js）

驾驶舱"飞向此处"（startFlyToOrder）分支逻辑（反压缩摘要）：

```js
// 更新分支：isFlyTo === true 时
if (p) {
  const s = { fly_to_id: team.flyToIdMap.get(gatewaySn) || "",   // ← 内存取值，刷新后为空
              start_point, points: [...], max_speed: 14 };
  await PUT("/atc/api/v1/flight-tasks/fly-to-points", s);        // ← 发出空 fly_to_id
}
// 空中新建分支 / 舱内起飞分支（POST），创建成功后：
team.setFlyToIdMap({ gatewaySn, flyToId: r.fly_to_id });
```

入口处虽有 in-flight 预检回填：

```js
const { data: a } = await GET(`/atc/api/v1/flight-tasks/in-flight?sn=${sn}`);
a?.fly_to_task?.uuid && team.setFlyToIdMap({ gatewaySn: sn, flyToId: a.fly_to_task.uuid });
```

但如 §4.3 所示，flyto 完成后 `fly_to_task.uuid` 已为空串，回填条件短路失效；而飞行会话 `flight_task_data.status=1` + `flight_type=2` 又使前端恢复出 `isFlyTo=true`，遂进入 PUT 更新分支并携带空 `fly_to_id`。

## 6. 根因结论

**服务端状态清理与前端状态恢复的不对称组合缺陷：**

1. flyto 子任务完成后，服务端清空 `in-flight.fly_to_task`（uuid=""），但飞行会话（flight_task_data，flyto 型）保持进行中（悬停待命，status=1）；
2. 前端依据"flyto 型会话进行中"恢复 `isFlyTo=true`，点击"飞向此处"被判定为"更新目标点"走 PUT；
3. 页面刷新/重进后前端内存 `flyToIdMap` 丢失，且唯一回填来源 `fly_to_task.uuid` 已被服务端清空 → PUT 请求 `fly_to_id=""` → 服务端 validator 以 200614 拒绝；
4. 用户侧无自愈手段（返航/降落结束会话前持续报错）。

## 7. 修复建议（任一即可闭环）

1. **服务端**：flyto 完成后不要清空 `in-flight.fly_to_task.uuid`（保留至飞行会话结束），或 `is_first_fly_to` 场景下回填 `takeoff_task_uuid`；
2. **前端（project-app）**：
   - in-flight 回填条件放宽为 `a.fly_to_task?.uuid || a.flight_task_data?.takeoff_task_uuid`；或
   - PUT 分支取不到 fly_to_id 时自动降级为 POST 新建；或
   - flyto 完成事件（wayline_ok 后）正确复位 `isFlyTo`，使后续点击走新建分支；
3. **服务端兼容**：`PUT fly-to-points` 收到空 `fly_to_id` 时按"新建"语义处理（或返回明确业务码引导前端重试新建），而非 validator 硬拒绝。

## 8. 期望行为

flyto 完成、飞机悬停期间（含页面刷新后），再次"飞向此处"应成功创建新的飞向任务（或正确更新原任务），不出现 200614。

## 9. 附件清单（可按需提供）

- es-task 容器 06:03–06:13 UTC 全量日志（含 §4 全部请求/响应）
- 浏览器 Network 截图（PUT 请求体与 200614 响应）
- `in-flight` 接口复测返回（2026-09-03 实测，fly_to_task.uuid 仍为空、flight_task_data.status=1）
- project-app/index.45879d02.js 反压缩分析片段（§5）
