# 数据库设计文档 · AiFlow v1.0

| 文档版本 | 修改日期 | 修改人 | 修改内容 |
|----------|----------|--------|----------|
| V1.0 | 2026-07-21 | Auto | 初稿：MySQL 8，基于 E-R 全量建模 |

**上游**：`docs/03-design/er/`、`docs/02-product/prototype/`  
**DDL**：[`ddl-v1.0.sql`](./ddl-v1.0.sql)

---

## 1. 设计概述

| 项目 | 内容 |
|------|------|
| 数据库类型 | MySQL 8.x |
| 库名 | `UAVPlatform` |
| 字符集 | `utf8mb4` |
| 排序规则 | `utf8mb4_unicode_ci` |
| 存储引擎 | InnoDB |
| 主键策略 | `BIGINT AUTO_INCREMENT` |
| 时间精度 | `DATETIME(6)` |
| JSON | 原生 `JSON`（几何、遥测、航线参数等） |
| 逻辑删除 | 业务表统一 `deleted` TINYINT：0 未删 / 1 已删 |
| 多租户预留 | 多数业务表含 `workspace_id`，本期默认 `1` |

### 1.1 命名规范

| 对象 | 规范 | 示例 |
|------|------|------|
| 表名 | `t_` + snake_case | `t_mission_plan` |
| 字段名 | snake_case | `work_status` |
| 主键 | `id` 或业务联合主键 | `pk` 隐式 PRIMARY KEY |
| 外键 | `fk_<本表缩写>_<引用>` | `fk_mission_device` |
| 普通索引 | `idx_<表缩写>_<字段>` | `idx_device_type_status` |
| 唯一索引 | `uk_<表缩写>_<字段>` | `uk_device_sn` |

### 1.2 设计原则

1. **枚举用 VARCHAR**，不用 MySQL ENUM，便于扩展。  
2. **跨限界上下文**：能建 FK 的建 FK（`RESTRICT`）；Analytics 读模型不强制 FK 到业务表。  
3. **航线快照表** `t_wayline_snapshot`：任务创建时固化航线，避免库内修改污染已下发计划。  
4. **设备状态** 1:1 拆表 `t_device_status`，降低主表写热点。  

---

## 2. E-R 到物理模型映射

| E-R 实体 | 物理表名 | 映射说明 |
|----------|----------|----------|
| Workspace（补充） | `t_workspace` | 单作业空间配置 |
| User | `t_user` | |
| Role | `t_role` | |
| Permission | `t_permission` | |
| RolePermission | `t_role_permission` | M:N 关联表 |
| Device | `t_device` | |
| DeviceStatus | `t_device_status` | 1:1，device_id PK |
| BindCode | `t_bind_code` | |
| DeviceIssueReport | `t_device_issue_report` | |
| AnnotationFolder | `t_annotation_folder` | |
| Annotation | `t_annotation` | geometry/style → JSON |
| SyncJob | `t_sync_job` | |
| SyncJobItem | `t_sync_job_item` | |
| WaylineFolder | `t_wayline_folder` | |
| Wayline | `t_wayline` | |
| WaylineContent | `t_wayline_content` | 1:1 |
| （任务用快照） | `t_wayline_snapshot` | 物理补充 |
| MissionPlan | `t_mission_plan` | |
| MissionExecution | `t_mission_execution` | |
| MediaFolder | `t_media_folder` | |
| MediaAsset | `t_media_asset` | |
| MediaTag | `t_media_tag` | |
| ModelAsset | `t_model_asset` | |
| ReconstructJob | `t_reconstruct_job` | |
| （重建-媒体） | `t_reconstruct_job_media` | M:N |
| WorkspaceQuota | `t_workspace_quota` | |
| DashboardSnapshot | `t_dashboard_snapshot` | 读模型 |
| FlightStatDaily | `t_flight_stat_daily` | 联合主键 ws+date |
| MissionStatDaily | `t_mission_stat_daily` | 联合主键 ws+date |
| Algorithm | `t_algorithm` | |
| AlgorithmAlarm | `t_algorithm_alarm` | |

| E-R 关系 | 映射方式 | 说明 |
|----------|----------|------|
| User N:1 Role | FK `role_id` | RESTRICT |
| Role M:N Permission | `t_role_permission` | CASCADE |
| Device 1:1 Status | PK=FK | CASCADE |
| Mission N:1 Device/Wayline | FK | RESTRICT |
| Mission 1:N Execution | FK | CASCADE |
| Media N:1 Execution | FK 可空 | SET NULL |
| Reconstruct M:N Media | 关联表 | |

---

## 3. 表结构定义（摘要）

> 完整字段、索引、注释以 [`ddl-v1.0.sql`](./ddl-v1.0.sql) 为准。本节按域列出核心字段与索引意图。

### 3.1 IAM

#### `t_user`

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| id | BIGINT AI | N | PK |
| workspace_id | BIGINT | N | 默认 1 |
| account | VARCHAR(128) | N | UK |
| display_name | VARCHAR(64) | N | |
| password_hash | VARCHAR(255) | N | |
| status | VARCHAR(32) | N | active/disabled |
| join_method | VARCHAR(32) | N | |
| joined_at / last_login_at | DATETIME(6) | | |
| role_id | BIGINT | N | FK → t_role |

**索引**：`uk_user_account`；`idx_user_role`；`idx_user_workspace_status`

#### `t_role` / `t_permission` / `t_role_permission`

见 DDL；预置 4 角色 + 权限点种子数据。

### 3.2 Device

#### `t_device`

| 字段 | 说明 |
|------|------|
| sn | UK，平台内唯一 |
| device_type | dock / aircraft / other |
| work_status | idle / working / debugging / offline / in_dock |
| firmware_* / flight_safety_db_version | 列表展示字段 |

**索引**：`uk_device_sn`；`idx_device_type_status`（分类 Tab + 状态）

#### `t_bind_code`

绑定码 UK；`expire_at`；状态 active/used/expired/revoked。

### 3.3 Map

#### `t_annotation`

| 字段 | 说明 |
|------|------|
| geom_type | point/line/polygon |
| purpose | work_area/no_fly/poi/other |
| geometry | JSON GeoJSON |
| sync_status | pending/synced/failed |

### 3.4 Wayline / Mission

#### `t_wayline` + `t_wayline_content` + `t_wayline_snapshot`

列表机型筛选走 `idx_wayline_model_time`。

#### `t_mission_plan`

关键查询：`idx_mission_status_schedule`（调度扫 pending）。

#### `t_mission_execution`

飞行统计回写 `flight_duration_sec` / `flight_distance_m` → Analytics 日表。

### 3.5 Media / Quota / Analytics / Algorithm

见 DDL：`t_media_asset`（分页时间序索引）、`t_workspace_quota`（1:1 workspace）、日统计联合主键、算法告警时间索引。

---

## 4. 核心字段字典

### 4.1 枚举字段

| 表名 | 字段名 | 枚举值 | 含义 |
|------|--------|--------|------|
| t_user | status | active / disabled | 启用 / 禁用 |
| t_device | device_type | dock / aircraft / other | 机场 / 飞行器 / 其它 |
| t_device | work_status | idle / working / debugging / offline / in_dock | 工作状态 |
| t_bind_code | status | active / used / expired / revoked | 绑定码状态 |
| t_annotation | geom_type | point / line / polygon | 几何类型 |
| t_annotation | purpose | work_area / no_fly / poi / other | 用途 |
| t_annotation | sync_status | pending / synced / failed | 同步状态 |
| t_wayline | wayline_type | waypoint / grid_2d / slope / geometry_fine | 航线类型 |
| t_wayline | status | draft / published | 草稿 / 已发布 |
| t_mission_plan | task_type | normal / wayline / manual | 任务类型 |
| t_mission_plan | strategy | immediate / once / recurring / continuous | 执行策略 |
| t_mission_plan | accuracy_mode | rtk / gnss | 精度 |
| t_mission_plan | signal_lost_action | rth / continue | 失联动作 |
| t_mission_plan / execution | status | draft/pending/running/paused/completed/failed/ended/partial | 见流转 |
| t_media_asset | media_type | photo / video / archive | 媒体类型 |
| t_media_asset | source | upload / livestream_record / mission_return / import | 来源 |
| t_media_asset | recycle_status | normal / in_trash / deleted | 回收状态 |
| t_algorithm | status | enabled / disabled / not_opened | 算法状态 |

### 4.2 状态流转 · 任务计划 / 执行

| 当前状态 | 触发动作 | 目标状态 |
|----------|----------|----------|
| draft | 确认创建 | pending |
| pending | 到点/立即执行 | running |
| pending | 暂停 | paused |
| paused | 恢复 | pending |
| running | 成功 | completed |
| running | 失败/超时 | failed |
| running | 人工结束 | ended |
| partial | 断点续飞 | 新 execution=pending |

设备 `work_status`：offline ↔ idle ↔ working；debugging 与任务互斥（应用层约束）。

---

## 5. 数据量预估（单作业空间 · 首年量级）

| 表名 | 预估行数 | 年增量 | 备注 |
|------|----------|--------|------|
| t_user | &lt; 100 | 低 | |
| t_device | &lt; 50 | 低 | |
| t_annotation | 1k～1万 | 中 | |
| t_wayline | 百～千 | 中 | |
| t_mission_plan | 千～万 | 中高 | |
| t_mission_execution | 万级 | 高 | 循环任务放大 |
| t_media_asset | 10万+ | 高 | 主存储在 OSS |
| t_algorithm_alarm | 万级 | 中 | P2 |
| t_flight_stat_daily | 365/年 | 固定 | 按日一行 |

**建议**：媒体元数据按 `created_at` 分区或归档（后续）；看板优先读日统计/快照，避免扫 execution 全表。

---

## 6. 与原型查询场景对照

| 原型场景 | 主要表 | 索引/策略 |
|----------|--------|-----------|
| 人员列表筛选角色 | t_user + t_role | role_id、status |
| 设备 Tab 分类 | t_device | device_type + work_status |
| 航线机型筛选 | t_wayline | aircraft_model + updated_at |
| 新建计划选设备/航线 | t_device / t_wayline | status=published |
| 任务记录列表 | t_mission_plan + execution | status、时间 |
| 媒体分页 | t_media_asset | media_type + created_at |
| 看板时间筛选 | t_*_stat_daily | PK (workspace_id, stat_date) |
| 绑定码弹窗 | t_bind_code | status=active |

---

## 7. 执行说明

1. 在 MySQL 8 执行：`source docs/03-design/database/ddl-v1.0.sql`  
2. **务必更换**种子用户 `admin@aiflow.local` 的 `password_hash`  
3. 生产环境可关闭或调整演示用 `t_bind_code` 初始值  
4. 外键较多，迁移工具若拆步执行请按 DDL 文件顺序  

---

## 8. 附件

- 建表 DDL → [`ddl-v1.0.sql`](./ddl-v1.0.sql)  
- E-R 汇总 → [`../er/er-summary.md`](../er/er-summary.md)  

**下游**：`tkxm-detail`（详设可引用表名/字段）；`tkxm-devplan` / 编码阶段按波次落库。
