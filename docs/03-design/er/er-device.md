# E-R · 机队设备（Device Fleet）

## 限界上下文

机场/飞行器/其它设备的绑定、状态、固件信息与定损反馈；对接现场端绑定码。

## 核心聚合

- **Device 聚合**：设备主数据与运行状态  
- **BindCode 聚合**：绑定码生命周期  
- **DeviceIssueReport 聚合**：定损/问题反馈  

```mermaid
erDiagram
    DEVICE ||--o| DEVICE_STATUS : has
    DEVICE ||--o{ DEVICE_ISSUE_REPORT : reports
    BIND_CODE ||--o{ DEVICE : used_to_bind
    DEVICE {
        bigint id PK
        string sn UK
        string name
        string device_type
        string model
        string firmware_version
        string firmware_upgrade_status
        string flight_safety_db_version
        datetime joined_at
        datetime online_at
        string work_status
    }
    DEVICE_STATUS {
        bigint device_id PK_FK
        string online_state
        string work_sub_status
        json telemetry_snapshot
        datetime updated_at
    }
    BIND_CODE {
        bigint id PK
        string code UK
        string workspace_id
        datetime expire_at
        string status
        datetime refreshed_at
    }
    DEVICE_ISSUE_REPORT {
        bigint id PK
        bigint device_id FK
        string description
        string tracking_no
        string status
        datetime created_at
        bigint created_by FK
    }
```

## 实体清单

| 实体 | 说明 | 聚合根 | 所属聚合 |
|------|------|--------|----------|
| Device | 设备 | 是 | Device |
| DeviceStatus | 运行状态快照 | 否 | Device |
| BindCode | 绑定码 | 是 | BindCode |
| DeviceIssueReport | 定损反馈 | 是 | DeviceIssueReport |

### Device

| 字段 | 类型 | 主键 | 外键 | 必填 | 说明 |
|------|------|------|------|------|------|
| id | bigint | Y | | Y | |
| sn | varchar(64) | | | Y | 平台内唯一 |
| name | varchar(128) | | | Y | |
| device_type | varchar(32) | | | Y | dock / aircraft / other |
| model | varchar(128) | | | Y | |
| firmware_version | varchar(64) | | | N | |
| firmware_upgrade_status | varchar(32) | | | N | latest / upgradable |
| flight_safety_db_version | varchar(64) | | | N | |
| joined_at | datetime | | | Y | |
| online_at | datetime | | | N | |
| work_status | varchar(32) | | | Y | idle / working / debugging / offline / in_dock |

## 领域事件

| 事件 | 触发 | 订阅方 |
|------|------|--------|
| DeviceBound | 绑定成功 | Analytics、Mission（可选校验） |
| DeviceStatusChanged | 心跳/任务驱动 | Analytics 看板机场环图 |
| DeviceUnbound | 解绑 | Mission 引用校验 |
| AnnotationSyncRequested | 地图发起同步 | Device 防腐层下发现场端 |
