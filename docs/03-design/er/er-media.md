# E-R · 资源库（Media Asset）

## 限界上下文

媒体/模型/设计文件与回收站；模型重建异步任务；存储用量。顶栏「数据管理」采用 **方案 A：等同资源库视图**。

## 核心聚合

- **MediaFolder / MediaAsset 聚合**  
- **ModelAsset 聚合**  
- **ReconstructJob 聚合**  
- **Quota 聚合**（平台级用量，可放 Analytics，此处保留写入方）  

```mermaid
erDiagram
    MEDIA_FOLDER ||--o{ MEDIA_ASSET : contains
    MEDIA_ASSET ||--o{ MEDIA_TAG : tagged
    MEDIA_ASSET ||--o{ RECONSTRUCT_JOB : source_of
    RECONSTRUCT_JOB ||--o| MODEL_ASSET : produces
    MEDIA_FOLDER {
        bigint id PK
        string name
        string category
        bigint parent_id FK
    }
    MEDIA_ASSET {
        bigint id PK
        bigint folder_id FK
        string name
        string media_type
        string payload
        bigint size_bytes
        string storage_key
        string source
        bigint mission_execution_id FK
        datetime created_at
        string recycle_status
    }
    MODEL_ASSET {
        bigint id PK
        string name
        string model_type
        string storage_key
        bigint size_bytes
        datetime created_at
        string recycle_status
    }
    RECONSTRUCT_JOB {
        bigint id PK
        string status
        datetime created_at
        datetime finished_at
        string error_message
        bigint created_by FK
    }
    WORKSPACE_QUOTA {
        bigint id PK
        bigint storage_total_bytes
        bigint storage_used_bytes
        int mapping_quota
        int mapping_used
        int livestream_minutes_total
        int livestream_minutes_used
    }
```

## 实体要点

| 实体 | 说明 | 聚合根 |
|------|------|--------|
| MediaAsset | 照片/视频/压缩包 | 是 |
| MediaFolder | 文件夹（巡检批次、直播回放等） | 是 |
| ModelAsset | 2D/3D/点云等 | 是 |
| ReconstructJob | 重建任务 | 是 |
| WorkspaceQuota | 配额用量 | 是 |

### MediaAsset.source

`upload` / `livestream_record` / `mission_return` / `import`

### recycle_status

`normal` / `in_trash` / `deleted`

## 领域事件

| 事件 | 订阅方 |
|------|--------|
| MediaUploaded | Analytics、Quota |
| MediaTrashed / Restored | Quota |
| ReconstructJobCompleted | ModelAsset、Analytics |
| QuotaChanged | Analytics 看板 |
