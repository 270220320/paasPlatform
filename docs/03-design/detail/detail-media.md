# 详细设计 · Media（U-050～U-053、U-080）· SDD

| 文档版本 | 修改日期 | 修改内容 |
|----------|----------|----------|
| V1.0 | 2026-07-22 | 初稿 |
| V2.0 | 2026-08-24 | **按实现校准**：存储实现为本地磁盘（接口形态保留 OSS 直传样式）；重建任务与任务级重建（U-086）共表，司空 Terra 为主通道；模型地址改 TEXT |

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

响应：ReconstructJobVO（jobId, status=pending）

#### 详情/列表 `GET /api/v1/reconstruct-jobs`、`GET /api/v1/reconstruct-jobs/{id}`

**ReconstructJobVO 增量字段（V2.0）**：missionPlanId, missionExecutionId, fh2TaskUuid, fh2ModelId, previewUrl, modelUrl

### 状态与实现（按实现校准）

pending → running → success | failed

- 重建主通道为**司空 Terra**（任务级入口见 mission 分册 U-086：按执行发起，源图在司空侧，无需 mediaIds）。
- `t_reconstruct_job` 同时服务两条入口：媒体选图重建（本地）与执行级重建（司空）；司空侧产物回填 fh2_model_id / preview_url / model_url（TEXT，签名 URL 可能超长）。
- 任务详情打开时懒校准（attachReconstruct → syncOne）纠正历史状态。
- 成功写入/关联 `t_model_asset`。

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
