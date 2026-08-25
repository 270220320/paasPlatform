# 详细设计 · Algorithm（U-070～U-071）· SDD

| 文档版本 | 修改日期 | 修改内容 |
|----------|----------|----------|
| V1.0 | 2026-07-22 | 初稿 |
| V2.0 | 2026-08-24 | **按实现校准**：后端 API 已实现；前端独立页面下线（/algorithms 重定向首页），能力保留供任务多模态校验与看板计数 |

**涉及表**：`t_algorithm` `t_algorithm_alarm`

**域错误码**

| 错误码 | 说明 |
|--------|------|
| 80001 | 算法不存在 |
| 80002 | 算法未开通不可启用 |

---

## U-070 · 算法列表与启停

**优先级**：P2 · **权限**：读 `algorithm:read` / 写 `algorithm:write`

### 接口

#### 列表 `GET /api/v1/algorithms`

**AlgorithmVO**：id, name, algoType, status, updatedAt

#### 能力 `GET /api/v1/algorithms/capabilities`

```json
{ "multimodalAvailable": false, "enabledDetectionCount": 0 }
```

供任务多模态开关（U-044）使用。

#### 更新状态 `PUT /api/v1/algorithms/{id}`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | string | 是 | enabled/disabled |
| config | object | 否 | |

若当前 `not_opened` → `80002`。

### 规则

启停后发事件刷新看板 algorithmCount。本期可以种子数据预置两条 detection + 一条 multimodal not_opened。

> **V2.0 前端状态**：`/algorithms` 路由已下线（重定向首页）。算法能力以 API 形态存在：`capabilities` 供任务创建时多模态开关校验（U-044），`algorithmCount`/`aiAlarmCount` 供看板（U-011）。后续如需管理页可复用既有 API 重建页面。

---

## U-071 · AI 告警记录

**优先级**：P2 · **权限**：`algorithm:read`

### 接口 `GET /api/v1/algorithm-alarms`

| 参数 | 说明 |
|------|------|
| algorithmId | 可选 |
| start/end | 时间 |
| page | 分页 |

**AlarmVO**：id, algorithmId, algorithmName, targetLabel, source, raisedAt, payload

写入方：检测服务回调（本期可模拟）`POST /api/v1/algorithm-alarms`（内部权限）→ 发 `AlarmRaised` → Analytics aiAlarmCount++。
