# 详细设计 · Media（U-050～U-053、U-080）· SDD

| 文档版本 | 修改日期 | 修改内容 |
|----------|----------|----------|
| V1.0 | 2026-07-22 | 初稿 |

**涉及表**：`t_media_*` `t_model_asset` `t_reconstruct_*` `t_workspace_quota`

**域错误码**

| 错误码 | 说明 |
|--------|------|
| 60001 | 媒体不存在 |
| 60002 | 存储配额不足 |
| 60003 | 上传凭证失败 |
| 60004 | 重建任务状态不允许 |
| 60005 | 功能未开通 |

---

## U-050 · 媒体库管理

**优先级**：P0 · **权限**：读 `library:read` / 写 `library:write`

### 接口

#### 上传凭证 `POST /api/v1/library/upload-credential`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| fileName | string | 是 | |
| mediaType | string | 是 | photo/video/archive |
| sizeBytes | long | 是 | 用于配额预检 |

**响应**：uploadUrl, storageKey, headers, expireAt

#### 确认入库 `POST /api/v1/library/media`

OSS 直传成功后回调元数据：folderId?, name, mediaType, payload?, sizeBytes, storageKey, source, missionExecutionId?, tags?

#### 列表 `GET /api/v1/library/media`

folderId, mediaType, keyword, page；默认 recycle_status=normal

#### 下载地址 `GET /api/v1/library/media/{id}/download-url`

#### 文件夹 CRUD ` /api/v1/library/folders`

#### 配额 `GET /api/v1/library/quota`

storageTotal/Used, mapping*, livestream*

### 业务规则

1. 预检 `used+size > total` → `60002`。  
2. 入库成功后累加 quota.storage_used_bytes，发 `MediaUploaded`。  
3. 分享链接本期可返回临时下载 URL（P1 能力可挂同一 download-url）。  

---

## U-051 · 模型 / 设计文件 / 回收站

**优先级**：P1 · **权限**：`library:write`

### 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/library/models` | 模型列表 |
| POST | `/api/v1/library/media/{id}/trash` | 进回收站 |
| POST | `/api/v1/library/media/{id}/restore` | 还原 |
| DELETE | `/api/v1/library/media/{id}/purge` | 彻底删除并扣减配额 |
| GET | `/api/v1/library/trash` | 回收站列表（media+model） |

设计文件可用 `t_media_asset.media_type=archive` + folder.category=design，或后续扩表；本期 category=design 文件夹约定。

### 规则

trash → recycle_status=in_trash；purge → deleted=1 且对象存储删除（异步）。

---

## U-052 · 模型重建任务

**优先级**：P1 · **权限**：`library:write`

### 接口

#### 创建 `POST /api/v1/reconstruct-jobs`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| mediaIds | long[] | 是 | 源照片 |

响应：jobId, status=pending

#### 详情/列表 `GET /api/v1/reconstruct-jobs` `GET /api/v1/reconstruct-jobs/{id}`

### 状态

pending → running → success|failed

成功时写 `t_model_asset` 并回填 model_id；发 `ReconstructJobCompleted`。

Worker 异步执行；接口只负责任务编排。

---

## U-053 · 智能分析入口

**优先级**：P2

### 接口 `GET /api/v1/library/features`

```json
{
  "smartSearch": false,
  "changeDetectionPro": false,
  "ultraPanorama": false
}
```

前端按钮根据 false 提示未开通；若强行调用对应 API → `60005`。

---

## U-080 · 数据管理入口

**优先级**：P1

### 规约

- **无独立后端资源**。  
- 前端路由 `/data` **重定向** `/library`（方案 A）。  
- 文档与菜单文案：「数据管理 · 资源库」。  
