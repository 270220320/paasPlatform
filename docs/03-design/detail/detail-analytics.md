# 详细设计 · Analytics（U-010～U-014、U-089 前端）· SDD

| 文档版本 | 修改日期 | 修改内容 |
|----------|----------|----------|
| V2.0 | 2026-08-24 | 按已实现代码补齐（V1.0 空缺）；首页改版为「看板 + 中央地图」形态 |

**涉及表**：`t_dashboard_snapshot`、`t_flight_stat_daily`、`t_mission_stat_daily`、`t_workspace_quota`（读模型）；直查 `t_device`、`t_wayline`、`t_media_asset`、`t_model_asset`、`t_user`、`t_algorithm`、`t_algorithm_alarm`、`t_mission_plan`、`t_mission_execution`、`t_safety_area`、`t_annotation`

**域错误码**：复用公共错误码（只读查询，无域内错误码）。

---

## 公共：查询参数与 VO

三个接口共用时间筛选（`TimeRangeHelper` 解析）：

| 参数 | 类型 | 说明 |
|------|------|------|
| range | string | 预置：week/month/lastWeek/lastMonth/halfYear/year |
| startDate / endDate | ISO 日期 | 自定义区间（与 range 二选一，自定义优先） |

**DashboardSummaryVO**

| 字段 | 类型 | 说明 |
|------|------|------|
| quota | QuotaVO | 存储配额/用量 |
| dockStatus | DockStatusVO | 机场状态分布（空闲/作业/调试/离线） |
| aircraftCount / relayCount / memberCount / waylineCount | int | 计数 |
| algorithmCount | int | AI 算法数 |
| mediaCount / modelCount / reportCount / aiAlarmCount | int | 数据成果 |
| automation | AutomationVO | 自动化指标（占位） |
| computedAt | string | 计算时间 |

**FlightStatsVO**：`sortieCount`、`durationSec`、`distanceM` + `series`(FlightStatPointVO[])
**MissionStatsVO**：`distribution`（计划/手动）、`result`（成功/暂停/失败）+ `series`(MissionStatPointVO[])

---

## U-010～U-011 · 看板资产与数据成果

**优先级**：P0 · **权限**：`dashboard:read`

### 接口 `GET /api/v1/dashboard/summary`

实现：`DashboardService.summary()` 直接聚合本地库计数（无独立投影表写入）；media/model 为真实数，report/aiAlarm 预留口径。

前端首页左栏「资产概览」面板消费：机场状态环图（ECharts Pie）+ 图例 + 飞行器/成员/航线/媒体计数格。

---

## U-012～U-013 · 飞行数据与任务图表

**优先级**：P0/P1 · **权限**：`dashboard:read`

### 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/dashboard/flight-stats` | 架次/时长/里程 + 时间序列 |
| GET | `/api/v1/dashboard/mission-stats` | 任务分布/执行结果 + 时间序列 |

数据来源：`t_mission_execution` 按执行起止时间聚合（架次=执行数、时长=duration 求和、里程=预留）。

前端：飞行数据 KPI 双卡；任务分布折线图 + 执行结果柱状图（ECharts 按需注册）。

---

## U-014 · 看板中央地图

**优先级**：P0 · **模块**：web（首页）

### 实现要点（已实现）

1. Leaflet 全屏底图（本地瓦片 tiles_tianjin，XYZ `{z}/{x}/{y}` 结构）。
2. 叠加图层：限飞区（`/safety/areas`，红色多边形/圆）+ 地图标注（`/annotations`）。
3. 在飞飞行器标记（U-089）：轮询 `/paas/aircraft/locations`，渲染机位图标与航向。
4. 图层显隐按本地存储记忆；缩放/拖拽与面板联动布局。

---

## U-089 · 在飞飞行器实时位置（前端消费）

数据由 fh2 分册 [`detail-fh2.md`](./detail-fh2.md) 的 `GET /api/v1/paas/aircraft/locations` 提供；首页按固定间隔轮询（页面不可见时暂停），字段：sn/name/model/modeCode/latitude/longitude/height/elevation/heading/updatedAt。
