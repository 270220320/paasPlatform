# UAVPlatform 工程说明

## 结构

```
AiFlow/
  backend/     Spring Boot 3.3 + MyBatis Plus + JWT + 司空 FH2 SDK 适配
  frontend/    Vue3 + Vite + Element Plus + paas.js（航线编辑 / 设备管理）
  docs/        产品与设计文档
```

## 前置

1. MySQL 8：执行 `docs/03-design/database/ddl-v1.0.sql`
2. **司空接入增量**：再执行 `docs/03-design/database/ddl-fh2-v1.1.sql`
3. 修改 `backend/src/main/resources/application.yml`：
   - 数据源账号密码
   - `uav.fh2.user-token` / `uav.fh2.binding-uuid`（司空下发）
4. JDK 21 + Maven；Node 18+
5. （可选）将 FlightHub Java SDK jar 放入 `backend/libs/`，见该目录 README

## 司空 PaaS 环境（当前）

| 项 | 值 |
|----|-----|
| frontHost（paas.js） | `http://192.168.76.141` |
| 真实 serverHost | `http://192.168.76.141:30812` |
| 浏览器侧 serverUrl（设备） | `${origin}/paas-api` |
| 浏览器侧 apiBaseUrl（航线） | `${origin}/paas-api/gateway` |
| Vite 代理 | `/paas-api` → `http://192.168.76.141:30812` |

- **前端**：`WaylineEditor`（航线创建/编辑）、`UranusDeviceManagerApp` / `FH2.loadDevice`（设备管理）
- **后端**：反射调用 FlightHub Java SDK（`getDeviceList` / `getWaylineList` 等）并同步到本地库
- Bootstrap API：`GET /api/v1/paas/bootstrap`

## 默认账号

- 账号：`admin@aiflow.local`
- 密码：`123456`

## 启动

```bash
# 库表
# mysql < docs/03-design/database/ddl-v1.0.sql
# mysql < docs/03-design/database/ddl-fh2-v1.1.sql

cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

浏览器：`http://127.0.0.1:5173`（Vite 代理 `/api` → `8080`）。

## 页面入口

| 功能 | 路径 |
|------|------|
| 司空航线编辑器 | `/waylines/editor?mode=create` 或 `mode=edit&waylineId=` |
| 司空设备管理 | `/devices/manager` |
| 航线列表（含同步） | `/waylines` |
| 设备列表（含同步） | `/devices` |
