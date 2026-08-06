# 详细设计总览 · AiFlow（SDD · 全量）

| 文档版本 | 修改日期 | 修改人 | 修改内容 |
|----------|----------|--------|----------|
| V1.0 | 2026-07-22 | Auto | SDD 全量初稿 |

---

## 1. 设计概述

### 1.1 范围

- **设计方法**：SDD（先定接口规约与契约）
- **覆盖功能点**：U-001～U-080 全量（含 P0/P1/P2）
- **涉及模块**：iam / device / map / wayline / mission / media / analytics / algorithm / web

### 1.2 引用文档

| 文档 | 路径 |
|------|------|
| 概要设计 | `docs/03-design/preliminary/preliminary-design.md` |
| 功能清单 U-ID | `docs/03-design/preliminary/feature-uid-list.md` |
| 数据库设计 | `docs/03-design/database/database-design-v1.0.md` |
| DDL | `docs/03-design/database/ddl-v1.0.sql` |
| 原型图 | `docs/02-product/prototype/index.html` |

### 1.3 文档拆分

| 文档 | 覆盖 U-ID |
|------|-----------|
| [detail-iam.md](./detail-iam.md) | U-001～U-004 |
| [detail-analytics.md](./detail-analytics.md) | U-010～U-014 |
| [detail-map.md](./detail-map.md) | U-020～U-023 |
| [detail-wayline.md](./detail-wayline.md) | U-030～U-032 |
| [detail-mission.md](./detail-mission.md) | U-040～U-044 |
| [detail-media.md](./detail-media.md) | U-050～U-053、U-080 |
| [detail-device.md](./detail-device.md) | U-060～U-062 |
| [detail-algorithm.md](./detail-algorithm.md) | U-070～U-071 |

---

## 2. 公共约定

### 2.1 API 基址与鉴权

| 项 | 约定 |
|----|------|
| Base Path | `/api/v1` |
| Content-Type | `application/json; charset=utf-8` |
| 鉴权头 | `Authorization: Bearer <accessToken>` |
| 可选兼容 | `X-User-Token: <accessToken>`（与司空 PaaS 习惯对齐） |
| 工作空间 | 服务端从 Token 解析 `workspaceId`，本期固定为 `1`；客户端无需传项目 ID |
| 分页 | `page` 从 1 起，`pageSize` 默认 50，最大 200 |
| 时间 | ISO-8601，库内 `DATETIME(6)`，响应统一字符串 `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` 或无时区本地串（实现选定一种并全局一致） |

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

## 3. 接口总览

| U-ID | 方法 | 路径 | 说明 |
|------|------|------|------|
| U-001 | POST | `/api/v1/auth/login` | 登录 |
| U-001 | POST | `/api/v1/auth/logout` | 登出 |
| U-001 | GET | `/api/v1/auth/me` | 当前用户 |
| U-002 | POST | `/api/v1/members` | 添加人员 |
| U-002 | GET | `/api/v1/members` | 人员列表 |
| U-003 | GET | `/api/v1/roles` | 角色列表 |
| U-003 | GET | `/api/v1/roles/{id}` | 角色详情含权限 |
| U-003 | PUT | `/api/v1/roles/{id}` | 更新角色权限 |
| U-003 | GET | `/api/v1/permissions` | 权限点目录 |
| U-004 | PUT | `/api/v1/members/{id}` | 编辑人员 |
| U-004 | DELETE | `/api/v1/members/{id}` | 删除/禁用人员 |
| U-010～013 | GET | `/api/v1/dashboard/summary` | 看板汇总 |
| U-012～013 | GET | `/api/v1/dashboard/flight-stats` | 飞行统计 |
| U-013 | GET | `/api/v1/dashboard/mission-stats` | 任务图表 |
| U-014 | — | 前端地图 SDK | 无独立业务写接口 |
| U-020 | CRUD | `/api/v1/annotations/**` | 标注 |
| U-021 | — | 前端量测 | 可不落库 |
| U-022 | POST | `/api/v1/annotations/sync` | 设备同步 |
| U-023 | GET | `/api/v1/map/tabs/meta` | Tab 能力开关 |
| U-030 | GET | `/api/v1/wayline-folders/**` | 文件夹 |
| U-030 | GET | `/api/v1/waylines` | 航线列表 |
| U-031 | POST/PUT/GET | `/api/v1/waylines/**` | 创建编辑 |
| U-032 | POST | `/api/v1/waylines/import` | 导入 |
| U-032 | GET | `/api/v1/waylines/{id}/export` | 导出 |
| U-040～042 | POST | `/api/v1/missions` | 新建计划 |
| U-041 | — | 调度 Worker | 内部 |
| U-043 | POST | `/api/v1/missions/{id}/actions/*` | 暂停/恢复等 |
| U-043 | GET | `/api/v1/missions` | 计划列表 |
| U-044 | — | 创建计划字段校验 | 见 mission |
| U-050 | CRUD | `/api/v1/library/media/**` | 媒体 |
| U-050 | POST | `/api/v1/library/upload-credential` | 上传凭证 |
| U-051 | POST | `/api/v1/library/*/trash|restore` | 回收站 |
| U-052 | POST/GET | `/api/v1/reconstruct-jobs/**` | 重建 |
| U-053 | GET | `/api/v1/library/features` | 能力开关 |
| U-060 | GET | `/api/v1/devices` | 设备列表 |
| U-061 | GET/POST | `/api/v1/bind-codes/**` | 绑定码 |
| U-061 | POST | `/api/v1/devices/bind` | 现场端绑定（服务端） |
| U-062 | POST/GET | `/api/v1/device-issues/**` | 定损 |
| U-070 | GET/PUT | `/api/v1/algorithms/**` | 算法 |
| U-071 | GET | `/api/v1/algorithm-alarms` | 告警 |
| U-080 | — | 路由等同资源库 | 前端 |

---

## 4. 权限码（与种子数据对齐）

`dashboard:read` · `map:read/write` · `wayline:read/write` · `mission:read/write` · `library:read/write` · `member:read/write` · `device:read/write` · `algorithm:read/write` · `role:manage`

接口权限在各分册「权限校验」节标注。

---

## 5. 附录

### 5.1 待确认事项

| # | 事项 | 建议默认 |
|---|------|----------|
| 1 | Token 形态 JWT vs 会话 Redis | Redis 存 sessionId + Bearer |
| 2 | 时间时区 | 服务器本地或 UTC，全局统一 |
| 3 | 现场端绑定协议细节 | `/devices/bind` 先定 HTTP JSON，厂商 ACL 后置 |

### 5.2 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| V1.0 | 2026-07-22 | SDD 全量初稿 |
