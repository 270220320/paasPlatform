# E-R 实体与聚合汇总

> 详情见各域文件；本文档供 `tkxm-database` / `tkxm-detail` 索引。

## 领域文件索引

| 领域 | 文件 |
|------|------|
| 上下文关系 | [er-context-map.md](./er-context-map.md) |
| 身份与权限 | [er-iam.md](./er-iam.md) |
| 机队设备 | [er-device.md](./er-device.md) |
| 地图标注 | [er-map.md](./er-map.md) |
| 航线 | [er-wayline.md](./er-wayline.md) |
| 任务计划 | [er-mission.md](./er-mission.md) |
| 资源库 | [er-media.md](./er-media.md) |
| 看板分析 | [er-analytics.md](./er-analytics.md) |
| 算法 | [er-algorithm.md](./er-algorithm.md) |

## E-3. 实体汇总清单

| 实体名称 | 所属上下文 | 所属聚合 | 类型 | 核心属性 |
|----------|------------|----------|------|----------|
| User | IAM | User | 实体 | account, role_id, status |
| Role | IAM | Role | 实体 | code, locked |
| Permission | IAM | Role | 实体 | module, action |
| Device | Device | Device | 实体 | sn, device_type, work_status |
| DeviceStatus | Device | Device | 实体 | online_state |
| BindCode | Device | BindCode | 实体 | code, expire_at |
| DeviceIssueReport | Device | DeviceIssueReport | 实体 | tracking_no |
| Annotation | Map | Annotation | 实体 | geometry, purpose |
| AnnotationFolder | Map | AnnotationFolder | 实体 | locked |
| SyncJob | Map | SyncJob | 实体 | status |
| Wayline | Wayline | Wayline | 实体 | aircraft_model, wayline_type |
| WaylineContent | Wayline | Wayline | 实体 | storage_key, structured_params |
| WaylineFolder | Wayline | WaylineFolder | 实体 | parent_id |
| MissionPlan | Mission | MissionPlan | 实体 | strategy, status |
| MissionExecution | Mission | MissionExecution | 实体 | flight_duration_sec |
| MediaAsset | Media | MediaAsset | 实体 | media_type, source |
| MediaFolder | Media | MediaFolder | 实体 | category |
| ModelAsset | Media | ModelAsset | 实体 | model_type |
| ReconstructJob | Media | ReconstructJob | 实体 | status |
| WorkspaceQuota | Media | Quota | 实体 | storage_*, livestream_* |
| DashboardSnapshot | Analytics | ReadModel | 读模型 | 各类计数 |
| FlightStatDaily | Analytics | ReadModel | 读模型 | sortie/duration/distance |
| MissionStatDaily | Analytics | ReadModel | 读模型 | success/fail |
| Algorithm | Algorithm | Algorithm | 实体 | status |
| AlgorithmAlarm | Algorithm | Algorithm | 实体 | raised_at |

## E-4. 聚合与聚合根

| 聚合名称 | 上下文 | 聚合根 | 内部组成 |
|----------|--------|--------|----------|
| User | IAM | User | — |
| Role | IAM | Role | Permission 关联 |
| Device | Device | Device | DeviceStatus |
| BindCode | Device | BindCode | — |
| DeviceIssueReport | Device | DeviceIssueReport | — |
| Annotation | Map | Annotation | Geometry/Style VO |
| SyncJob | Map | SyncJob | SyncJobItem |
| Wayline | Wayline | Wayline | WaylineContent |
| MissionPlan | Mission | MissionPlan | （引用执行） |
| MissionExecution | Mission | MissionExecution | breakpoint VO |
| MediaAsset | Media | MediaAsset | tags |
| ReconstructJob | Media | ReconstructJob | — |
| Algorithm | Algorithm | Algorithm | Alarm |

## E-5. 领域事件总表

| 事件名称 | 发布方 | 订阅方 |
|----------|--------|--------|
| UserCreated / UserDisabled | IAM | Analytics |
| DeviceBound / DeviceStatusChanged | Device | Analytics, Mission |
| DeviceSyncRequested | Map | Device |
| WaylineCreated / WaylinePublished | Wayline | Analytics, Mission |
| MissionStarted / Completed / Failed | Mission | Device, Media, Analytics |
| MediaUploaded / QuotaChanged | Media | Analytics |
| ReconstructJobCompleted | Media | Analytics |
| AlarmRaised | Algorithm | Analytics |
