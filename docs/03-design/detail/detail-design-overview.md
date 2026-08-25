# 详细设计总览 · UAVPlatform（SDD · 全量）

| 文档版本 | 修改日期 | 修改人 | 修改内容 |
|----------|----------|--------|----------|
| V1.0 | 2026-07-22 | Auto | SDD 全量初稿 |
| V2.0 | 2026-08-24 | Auto | **按已实现代码校准**：项目定名 UAVPlatform；补齐 iam/analytics/wayline 空缺分册；新增 fh2、safety/workarea 分册；接口总览补 U-081～U-091；鉴权实现落定（JWT + X-User-Token 兼容） |

---

## 1. 设计概述

### 1.1 范围

- **设计方法**：SDD（先定接口规约与契约）
- **覆盖功能点**：U-001～U-091 全量（含 P0/P1/P2，含实现状态）
- **涉及模块**：iam / device / map / safety / workarea / wayline / mission / media / analytics / algorithm / fh2 / web（后端包 `com.uavplatform.*`）
- **代码基线**：与概要设计 V2.0 同步（2026-08）

### 1.2 引用文档

| 文档 | 路径 |
|------|------|
| 概要设计 | `docs/03-design/preliminary/preliminary-design.md`（V2.0） |
| 功能清单 U-ID | `docs/03-design/preliminary/feature-uid-list.md`（V2.0） |
| 数据库设计 | `docs/03-design/database/database-design-v1.0.md` + `ddl-fh2-v1.1.sql` + `alter-*.sql` |
| DDL | `docs/03-design/database/ddl-v1.0.sql` |
| 原型图 | `docs/02-product/prototype/index.html` |

### 1.3 文档拆分

| 文档 | 覆盖 U-ID |
|------|-----------|
| [detail-iam.md](./detail-iam.md) | U-001～U-004、U-087 |
| [detail-analytics.md](./detail-analytics.md) | U-010～U-014、U-089（前端部分） |
| [detail-map.md](./detail-map.md) | U-020～U-023 |
| [detail-safety.md](./detail-safety.md) | U-081、U-082 |
| [detail-wayline.md](./detail-wayline.md) | U-030～U-032 |
| [detail-mission.md](./detail-mission.md) | U-040～U-044、U-085（入口）、U-086、U-091 |
| [detail-media.md](./detail-media.md) | U-050～U-053、U-080 |
| [detail-device.md](./detail-device.md) | U-060～U-062 |
| [detail-fh2.md](./detail-fh2.md) | U-083～U-085、U-088（司空集成通道与前端组件装载） |
| [detail-algorithm.md](./detail-algorithm.md) | U-070～U-071 |

---

## 2. 公共约定

### 2.1 API 基址与鉴权（实现落定）

| 项 | 约定 |
|----|------|
| Base Path | `/api/v1` |
| Content-Type | `application/json; charset=utf-8` |
| 鉴权头 | `Authorization: Bearer <jwt>`（AuthInterceptor 解析） |
| 可选兼容 | `X-User-Token: <jwt>`（与司空 PaaS 习惯对齐） |
| 鉴权实现 | JWT（jjwt 签发）；登出/删人写入内存 TokenBlacklist 即时失效 |
| 工作空间 | 服务端固定 workspaceId=1；客户端无需传 |
| 分页 | `page` 从 1 起，`pageSize` 默认 50 |
| 时间 | 库内 `DATETIME(6)`；响应 `yyyy-MM-dd HH:mm:ss` 本地串（司空对接沿用同格式） |
| 司空侧调用 | 经 `uav.fh2.*` 配置（或 `t_fh2_config` 覆盖）；SDK 通道用 Java SDK，REST 通道走 `route-api-base` |

### 2.2 统一响应包

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "01HZX..."
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | `0` 成功；非 0 失败（见错误码） |
| message | string | 可读提示 |
| data | object/array/null | 业务数据 |
| requestId | string | 链路追踪 |

分页 `data` 形态：

```json
{
  "list": [],
  "total": 100,
  "page": 1,
  "pageSize": 50
}
```

### 2.3 公共错误码

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 0 | 成功 | — |
| 40000 | 参数校验失败 | 检查字段 |
| 40100 | 未登录/Token 无效 | 跳转登录 |
| 40101 | Token 过期 | 刷新或重登 |
| 40300 | 无权限 | 提示无权限 |
| 40400 | 资源不存在 | 刷新列表 |
| 40900 | 状态冲突 | 按提示重试 |
| 42900 | 限流 | 稍后重试 |
| 50000 | 系统内部错误 | 联系运维并带 requestId |

业务域错误码见各分册（1xxxx IAM、2xxxx Device…）。

### 2.4 公共 DTO

#### ApiResult\<T\>

见 2.2。

#### PageQuery

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 50 |
| keyword | string | 否 | 模糊搜索 |

#### PageResult\<T\>

| 字段 | 类型 | 说明 |
|------|------|------|
| list | T[] | |
| total | long | |
| page | int | |
| pageSize | int | |

#### IdVO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 新建资源主键 |

---

## 3. 接口总览（V2.0 · 按代码校准）

| U-ID | 方法 | 路径 | 说明 |
|------|------|------|------|
| U-001 | POST | `/api/v1/auth/login` | 登录 |
| U-001 | POST | `/api/v1/auth/logout` | 登出（黑名单） |
| U-001 | GET | `/api/v1/auth/me` | 当前用户 |
| U-087 | PUT | `/api/v1/auth/email` | 修改邮箱 |
| U-087 | PUT | `/api/v1/auth/password` | 修改密码 |
| U-002 | POST/GET | `/api/v1/members` | 人员添加/列表 |
| U-004 | PUT/DELETE | `/api/v1/members/{id}` | 编辑/删除人员 |
| U-003 | GET | `/api/v1/roles`、`/api/v1/roles/options` | 角色列表/下拉项 |
| U-003 | GET/PUT | `/api/v1/roles/{id}` | 角色详情/更新权限 |
| U-003 | GET | `/api/v1/permissions` | 权限点目录 |
| U-010～013 | GET | `/api/v1/dashboard/summary` | 看板汇总（range/startDate/endDate） |
| U-012～013 | GET | `/api/v1/dashboard/flight-stats` | 飞行统计 |
| U-013 | GET | `/api/v1/dashboard/mission-stats` | 任务图表 |
| U-014 | — | 前端 Leaflet 地图 | 无独立业务写接口 |
| U-020 | CRUD | `/api/v1/annotations/**`、`/annotation-folders/**` | 标注与文件夹 |
| U-021 | — | 前端量测 | 不落库 |
| U-022 | POST/GET | `/api/v1/annotations/sync`、`/sync/{jobId}` | 同步任务/查询 |
| U-023 | GET | `/api/v1/map/tabs/meta` | Tab 能力开关 |
| U-081 | CRUD | `/api/v1/safety/areas/**` | 限飞区 |
| U-081 | POST/GET | `/api/v1/safety/sync`、`/sync-status` | 推送司空/同步状态 |
| U-082 | CRUD | `/api/v1/work-areas/**` | 作业区 |
| U-030 | GET/POST/DELETE | `/api/v1/wayline-folders/**` | 文件夹（含删除） |
| U-030 | GET | `/api/v1/waylines` | 航线列表 |
| U-031 | POST/PUT/GET | `/api/v1/waylines/**` | 创建编辑详情 |
| U-031 | DELETE | `/api/v1/waylines/uuid/{waylineUuid}` | 按司空 uuid 删除镜像 |
| U-032 | POST | `/api/v1/waylines/import` | 导入 |
| U-032 | GET | `/api/v1/waylines/{id}/export` | 导出 |
| U-040～042 | POST | `/api/v1/missions` | 新建计划（含安全参数） |
| U-041 | — | MissionScheduler | 内部调度 |
| U-043 | GET | `/api/v1/missions`、`/api/v1/missions/{id}` | 计划列表/详情（含执行记录） |
| U-043 | POST | `/api/v1/missions/{id}/actions/pause|resume|cancel|copy` | 动作 |
| U-043 | DELETE | `/api/v1/missions/{id}` | 删除 |
| U-086 | POST | `/api/v1/missions/{planId}/executions/{executionId}/reconstruct` | 按执行发起重建 |
| U-086 | GET | `/api/v1/missions/{planId}/executions/{executionId}/reconstruct/model-url` | 模型 ZIP 下载地址 |
| U-091 | DELETE | `/api/v1/missions/{planId}/executions/{executionId}/media/{mediaId}` | 删除执行媒体 |
| U-050 | POST | `/api/v1/library/upload-credential` | 上传凭证 |
| U-050 | POST/GET | `/api/v1/library/media` | 确认入库/列表 |
| U-050 | GET | `/api/v1/library/media/{id}/download-url` | 下载地址 |
| U-050 | GET/POST | `/api/v1/library/folders` | 文件夹 |
| U-050 | GET | `/api/v1/library/quota` | 配额 |
| U-051 | GET | `/api/v1/library/models` | 模型列表 |
| U-051 | POST/DELETE | `/api/v1/library/media/{id}/trash|restore|purge`、`/library/trash` | 回收站 |
| U-052 | POST/GET | `/api/v1/reconstruct-jobs/**` | 重建任务 |
| U-053 | GET | `/api/v1/library/features` | 能力开关 |
| U-060 | GET | `/api/v1/devices` | 设备列表（deviceType/keyword/workStatus） |
| U-061 | GET/POST | `/api/v1/bind-codes/current|refresh` | 绑定码 |
| U-061 | POST | `/api/v1/devices/bind` | 现场端绑定 |
| U-062 | POST/GET | `/api/v1/device-issues/**` | 定损 |
| U-070 | GET/PUT | `/api/v1/algorithms/**`（含 `/capabilities`） | 算法 |
| U-071 | GET/POST | `/api/v1/algorithm-alarms` | 告警 |
| U-080 | — | `/data` → `/library` 重定向 | 前端 |
| U-083 | GET | `/api/v1/paas/bootstrap` | paas.js 装载配置 |
| U-083 | GET/POST | `/api/v1/paas/sdk/status`、`/sdk/reload` | SDK 状态/重载 |
| U-088 | POST | `/api/v1/paas/devices/sync`、`/waylines/sync` | 设备/航线镜像同步 |
| U-088 | GET | `/api/v1/paas/devices/remote` | 远端设备预览 |
| U-083 | POST | `/api/v1/paas/waylines/upsert` | 编辑器保存回写镜像 |
| U-089 | GET | `/api/v1/paas/aircraft/locations` | 在飞飞行器实时位置 |

---

## 4. 权限码（与种子数据对齐）

`dashboard:read` · `map:read/write` · `wayline:read/write` · `mission:read/write` · `library:read/write` · `member:read/write` · `device:read/write` · `algorithm:read/write` · `role:manage`

> 说明：safety/workarea 复用 `map:read/write`（空域安全属于地图作业域）；fh2 通道按目标资源校验（bootstrap 要求 `device:read|wayline:read|dashboard:read` 任一，reload 要求 `role:manage`，同步要求 `device:write`/`wayline:write`）。

接口权限在各分册「权限校验」节标注。

---

## 5. 附录

### 5.1 待确认事项（V2.0 复核）

| # | 事项 | 结论（现状） |
|---|------|----------|
| 1 | Token 形态 JWT vs 会话 Redis | **已定：JWT + 内存 TokenBlacklist**（重启失效由过期兜底） |
| 2 | 时间时区 | **已定：服务器本地时区（Asia/Shanghai）**，格式 `yyyy-MM-dd HH:mm:ss` |
| 3 | 现场端绑定协议细节 | **已定：`/devices/bind` HTTP JSON + 绑定码**；司空同步设备为主通道 |

### 5.2 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| V1.0 | 2026-07-22 | SDD 全量初稿 |
| V2.0 | 2026-08-24 | 按实现校准：补齐 iam/analytics/wayline 分册；新增 fh2、safety 分册；接口总览与公共约定更新 |
