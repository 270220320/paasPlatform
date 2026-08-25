# 详细设计 · Device（U-060～U-062）· SDD

| 文档版本 | 修改日期 | 修改内容 |
|----------|----------|----------|
| V1.0 | 2026-07-22 | 初稿 |
| V2.0 | 2026-08-24 | **按实现校准**：t_device 增加 gateway_sn/source 等镜像列；设备管理页内嵌司空 DeviceManager 组件；同步能力移至 fh2 分册（U-088） |

**域错误码**

| 错误码 | 说明 |
|--------|------|
| 20001 | 设备 SN 已存在 |
| 20002 | 绑定码无效或过期 |
| 20003 | 设备不存在 |
| 20004 | 设备类型不匹配 |
| 20005 | 无在线设备可同步（供 Map 调用时透传） |

---

## U-060 · 设备分类列表

**优先级**：P0 · **权限**：`device:read`

### 接口 `GET /api/v1/devices`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceType | string | 否 | dock/aircraft/other；对应 Tab |
| keyword | string | 否 | SN 或名称 |
| workStatus | string | 否 | |
| page/pageSize | int | 否 | |

**DeviceVO**（按实现）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | |
| sn | string | |
| gatewaySn | string | 网关/机场 SN（子设备时有值） |
| name | string | |
| deviceType | string | dock/aircraft/other |
| model | string | |
| fh2DeviceModelKey | string | FH2 型号枚举键 |
| source | string | local/fh2（镜像来源） |
| firmwareVersion | string | |
| workStatus | string | |
| joinedAt | string | |
| onlineAt | string | |
| fh2SyncedAt | string | 最近司空同步时间 |

### 业务规则（按实现校准）

1. 默认过滤 `deleted=0`。
2. Tab 切换仅改 `deviceType`，互斥列表。
3. 设备列表页内嵌司空 DeviceManager（UranusDeviceManagerApp）组件：绑定/调试/固件等实时操作走司空；本地库为镜像（source=fh2），另有本地绑定设备（source=local）。
4. 「同步司空设备」触发 `POST /api/v1/paas/devices/sync`（见 fh2 分册 U-088）；unmount 后还原全局主题变量避免其它页面被刷白。
5. 固件版本/飞行安全库字段展示以司空侧为准，本地列为镜像快照。  

---

## U-061 · 设备绑定码

**优先级**：P0 · **权限**：查看/刷新 `device:write`；现场绑定接口用绑定码鉴权（非用户 Token）

### 接口

#### 获取当前有效绑定码 `GET /api/v1/bind-codes/current`

**BindCodeVO**：code, workspaceCode, workspaceId, expireAt, status

#### 刷新绑定码 `POST /api/v1/bind-codes/refresh`

- 旧码 `revoked`；生成新码，默认有效期 7 天（可配置）  
- 响应：BindCodeVO  

#### 现场端绑定 `POST /api/v1/devices/bind`（服务端开放给现场/机场代理）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| workspaceCode | string | 是 | 如 aiflow-workspace-001 |
| bindCode | string | 是 | |
| sn | string | 是 | |
| name | string | 是 | |
| deviceType | string | 是 | |
| model | string | 是 | |
| firmwareVersion | string | 否 | |

**规则**

1. 校验 workspace + bindCode status=active 且未过期，否则 `20002`。  
2. SN 冲突 `20001`。  
3. 成功插入 `t_device` + `t_device_status(offline/idle)`；可选将 bindCode 标记 used 或保持可多次绑定（**默认：绑定码在有效期内可绑定多台**，仅刷新会作废）。  
4. 发布领域事件 `DeviceBound`。  

```mermaid
sequenceDiagram
  participant Field as 现场端
  participant API as DeviceAPI
  participant DB as DB
  Field->>API: POST /devices/bind
  API->>DB: 校验 bind_code + workspace
  API->>DB: insert device + status
  API-->>Field: DeviceVO
```

---

## U-062 · 设备定损反馈

**优先级**：P1 · **权限**：`device:write`

### 接口

#### 创建 `POST /api/v1/device-issues`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceId | long | 是 | |
| description | string | 是 | ≤1000 |

**响应**：id, trackingNo（服务端生成，如 `ISSUE-yyyyMMdd-******`）, status=submitted

#### 列表 `GET /api/v1/device-issues?deviceId=`

### 业务规则

设备不存在 → `20003`；创建后状态 submitted → processing → closed（后台人工，本期可仅 submitted）。
