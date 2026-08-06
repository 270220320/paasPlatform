# E-R · 航线（Wayline）

## 限界上下文

航线库：文件夹、航线主数据、KMZ/结构化参数；被任务计划引用。

## 核心聚合

- **WaylineFolder 聚合**  
- **Wayline 聚合**：航线定义与版本信息  

```mermaid
erDiagram
    WAYLINE_FOLDER ||--o{ WAYLINE : contains
    WAYLINE ||--o| WAYLINE_CONTENT : has
    WAYLINE_FOLDER {
        bigint id PK
        string name
        bigint parent_id FK
    }
    WAYLINE {
        bigint id PK
        bigint folder_id FK
        string name
        string aircraft_model
        string wayline_type
        datetime updated_at
        bigint updated_by FK
        string status
    }
    WAYLINE_CONTENT {
        bigint wayline_id PK_FK
        string storage_key
        json structured_params
        string checksum
    }
```

## 实体清单

| 实体 | 说明 | 聚合根 | 所属聚合 |
|------|------|--------|----------|
| Wayline | 航线 | 是 | Wayline |
| WaylineContent | 文件/参数 | 否 | Wayline |
| WaylineFolder | 文件夹 | 是 | WaylineFolder |

### Wayline

| 字段 | 类型 | 主键 | 外键 | 必填 | 说明 |
|------|------|------|------|------|------|
| id | bigint | Y | | Y | |
| folder_id | bigint | | Folder.id | N | |
| name | varchar(128) | | | Y | ≤60 字（产品约束） |
| aircraft_model | varchar(64) | | | Y | 机型筛选 |
| wayline_type | varchar(32) | | | Y | waypoint / grid_2d / slope / geometry_fine / ... |
| status | varchar(32) | | | Y | draft / published |
| updated_at | datetime | | | Y | |
| updated_by | bigint | | User.id | N | |

### WaylineContent

| 字段 | 类型 | 主键 | 外键 | 必填 | 说明 |
|------|------|------|------|------|------|
| wayline_id | bigint | Y | Wayline.id | Y | |
| storage_key | varchar(512) | | | N | 对象存储中的 KMZ |
| structured_params | json | | | N | 编辑器结构化数据 |
| checksum | varchar(64) | | | N | |

## 领域事件

| 事件 | 触发 | 订阅方 |
|------|------|--------|
| WaylineCreated / Updated | 保存 | Analytics（航线数） |
| WaylinePublished | 发布可用 | Mission 可选列表 |
| WaylineDeleteBlocked | 被执行中计划引用 | 前端提示 |
