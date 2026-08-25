# 详细设计 · FH2 司空集成（U-083～U-085、U-088）· SDD

| 文档版本 | 修改日期 | 修改内容 |
|----------|----------|----------|
| V2.0 | 2026-08-24 | 新增分册：司空 2 私有化集成通道（后端 `/api/v1/paas/**` + 前端 paas.js 组件装载） |

**涉及表**：`t_fh2_config`（接入配置）；回写 `t_device`（gateway_sn/source/fh2_synced_at）、`t_wayline`（fh2_wayline_uuid/source）、`t_workspace`（fh2_binding_uuid）

**域错误码**：复用公共错误码 + `MISSION_REMOTE_ERROR`（司空调用失败透传）。

---

## 公共：配置双源与 SDK 封装

### 配置双源（Fh2RuntimeConfigHolder）

优先级：`t_fh2_config`（库内，动态改配） > `application.yml` 的 `uav.fh2.*`（静态兜底）。

| 配置项 | 说明 |
|--------|------|
| enabled | 集成开关 |
| frontHost / serverHost / gatewayBase / wssUrl | 司空站点与网关 |
| userToken | X-User-Token（前端 paas.js 与后端 SDK 共用） |
| bindingUuid | 工作空间绑定（缺省从 token claims.organizationUuid 推导） |
| wayline/device/replaySdk(Css/Js) | 各前端组件资源地址 |
| routeApiBase | dji2 REST 基址（周期任务/安全区推送） |
| extraHttpHeaderKey/Value | 网关额外鉴权头 |

### SDK 封装（ReflectiveFh2SdkClient）

- 以**反射**调用 FlightHub Java SDK（libs 目录 jar），避免编译期强依赖。
- `available()` / `initError()` 暴露初始化状态；`refresh()` 支持运行时重载。
- 能力：listDevices、listAircraft、listWaylines、飞行任务创建/详情、Terra 重建（取 folder/发起/状态/模型）。

---

## U-083 · 司空集成通道

**优先级**：P0 · **权限**：bootstrap 需 `device:read|wayline:read|dashboard:read` 任一；reload 需 `role:manage`

### 接口（PaasController `/api/v1/paas`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/bootstrap` | 前端装载 paas.js 所需配置 + userToken |
| GET | `/sdk/status` | enabled / sdkAvailable / initError |
| POST | `/sdk/reload` | 重载 SDK（换配置后使用） |

**PaasBootstrapVO**：enabled, frontHost, serverHost, gatewayBase, wssUrl, userToken, bindingUuid, organizationUuid, projectUuid, objectKeyPrefix, wayline/device/replaySdkCss/Js, authHeaderName(=X-User-Token)

前端 `usePaasBootstrap` composable 统一消费：先 fetch bootstrap → 动态注入 paas.js 与组件 css → 缓存 authConfig（initConfig/initNotifyCenter/loadMap 参数）供各页面复用。

---

## U-088 · 司空设备/航线镜像同步

**优先级**：P1 · **权限**：设备同步 `device:write`；航线同步/upsert `wayline:write`

### 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/devices/sync` | 拉取司空设备拓扑 + 机场挂载飞行器，upsert 本地 |
| GET | `/devices/remote` | 远端设备预览（不落库） |
| POST | `/waylines/sync` | 拉取司空航线列表，upsert 本地镜像 |
| POST | `/waylines/upsert` | WaylineEditor 保存后回写镜像（body: waylineUuid 必填, name?/folderId?） |

### 同步规则

1. 设备合并策略：拓扑（机场为主）+ listAircraft 按 SN 去重（`putIfAbsent`），保证 aircraft 不缺。
2. upsert 以 SN（设备）/ fh2_wayline_uuid（航线，唯一键）判存在；`source=fh2`，`fh2_synced_at=now`。
3. upsert 航线 name 为空时，服务端反查司空最新名称。
4. 响应 `{synced: n}`；失败抛 `MISSION_REMOTE_ERROR` 并保留 initError 细节。

---

## U-084 · 驾驶舱（前端）

**优先级**：P0 · **模块**：web（`/cockpit`、`/cockpit/detail`，全屏路由）

### 实现要点

1. 列表页：本地设备库（dock/aircraft）中选择目标，携带 `gatewaySn + droneSn + name` 跳转详情页。
2. 详情页：装载 paas.js → `initConfig` → `initNotifyCenter` → `loadMap`（共享 `#map-app-global` 容器）→ `loadCockpit(domId, {gateway_sn, drone_sn, map:true})`。
3. 页面骨架提供司空微前端容器（cockpit-header / project-app / right-micro-app / dock-live）。
4. 切页前 `destroyCockpit` + `resetFh2SharedMapContext` 卸载地图态并清理游离全局容器，防止航线库地图挂到 0 尺寸节点。
5. 状态文案展示 loading/失败；失败可重试（重建骨架 key）。

---

## U-085 · 飞行回放（前端）

**优先级**：P1 · **模块**：web（`/missions/:planId/replay/:executionId`，全屏路由）

### 实现要点

1. 入口：任务详情页执行记录行「回放」按钮（要求该执行已有 `fh2_task_uuid`，否则禁用并提示）。
2. 装载流程与驾驶舱一致（paas.js → initConfig → loadMap），随后实例化 `FlyDataReplay({taskId: fh2_task_uuid, ...authConfig})` 并 `mount`。
3. 错误展示 + 重试按钮（重置 DOM key 重新初始化）。
4. 组件销毁：`destroy()` + 卸载共享地图上下文。
