# 冒烟用例 · AiFlow

> 从测试用例中提取 P0 级核心用例，覆盖主流程链路，用于快速验证构建可用性

| 文档版本 | 修改日期 | 修改人 | 修改内容 |
|----------|----------|--------|----------|
| V1.0 | 2026-07-30 | Auto | 初稿 |
| V1.1 | 2026-09-20 | Auto | 首次执行留痕；修正 SC-001 密码说明与 SC-002 必填字段（aircraftModel）；SC-003 未执行说明 |

---

## 冒烟范围

覆盖 V1.0 五大核心主流程，确保每次构建后主链路可用：
1. 登录与权限
2. 航线创建与发布
3. 飞行任务建立即执行
4. 媒体采集与列表
5. 设备绑定与驾驶舱展示

---

## SC-001 · 超管登录全链路

- **关联功能点**：U-001
- **主流程描述**：超管账号登录 → 获取当前用户 → 登出
- **前置条件**：种子账号 `admin@aiflow.local / admin123` 已存在（2026-09-20 密码已重置对齐本口径）
- **测试环境**：后端 8080 启动，前端 5173 启动（纯 API 冒烟可不起前端）

| 步骤 | 操作描述 | 输入数据 | 预期结果 | 实际结果 | 通过 |
|------|----------|----------|----------|----------|:----:|
| 1 | POST /api/v1/auth/login | admin@aiflow.local/admin123 | code=0，返回 accessToken | code=0，accessToken 正常 | ✅ |
| 2 | GET /api/v1/auth/me | 带 Token | 返回 super_admin 角色 | super_admin | ✅ |
| 3 | POST /api/v1/auth/logout | 带 Token | code=0，Token 失效 | Token 失效 | ✅ |
| 4 | 再用旧 Token 调 /auth/me | 旧 Token | code=40100 | code=40100 | ✅ |

---

## SC-002 · 建航线并发布供任务使用

- **关联功能点**：U-030/031
- **主流程描述**：建文件夹 → 创建航线 → 发布
- **前置条件**：已登录且具备 `wayline:write`

| 步骤 | 操作描述 | 输入数据 | 预期结果 | 实际结果 | 通过 |
|------|----------|----------|----------|----------|:----:|
| 1 | POST /api/v1/wayline-folders | name=冒烟 | code=0 | code=0，id 正常 | ✅ |
| 2 | POST /api/v1/waylines | name=SC02, waylineType=waypoint, status=draft, **aircraftModel=M350RTK（必填）** | code=0 | code=0，status=draft（缺 aircraftModel 会 40000） | ✅ |
| 3 | PUT /api/v1/waylines/{id} | status=published | code=0 | code=0 | ✅ |
| 4 | GET /api/v1/waylines/{id} | — | status=published | status=published | ✅ |

---

## SC-003 · 建立即飞行任务并跟踪状态

- **关联功能点**：U-040/041/043
- **主流程描述**：建任务 → 跟踪 pending→running→completed → 删除
- **前置条件**：设备已绑定、航线已发布、司空 PaaS 可达

| 步骤 | 操作描述 | 输入数据 | 预期结果 | 实际结果 | 通过 |
|------|----------|----------|----------|----------|:----:|
| 1 | POST /api/v1/missions | strategy=immediate | code=0，status=pending | | |
| 2 | GET /api/v1/missions/{id} | — | 含 execution 记录 | | |
| 3 | 轮询执行记录（5s） | — | 最终 completed/failed | | |
| 4 | DELETE /api/v1/missions/{id} | 非运行中 | code=0 | | |

---

## SC-004 · 媒体上传与倒序列表

- **关联功能点**：U-050/051
- **主流程描述**：取上传凭证 → 上传 → 确认入库 → 列表倒序
- **前置条件**：已登录 `library:read/write`

| 步骤 | 操作描述 | 输入数据 | 预期结果 | 实际结果 | 通过 |
|------|----------|----------|----------|----------|:----:|
| 1 | POST /api/v1/library/upload-credential | 一张照片 | 返回 uploadUrl/storageKey | | |
| 2 | PUT OSS 直传 | 真实文件 | HTTP 200 | | |
| 3 | POST /api/v1/library/media | storageKey | code=0 | | |
| 4 | GET /api/v1/library/media | page=1 | 首项为最新 createdAt | | |

---

## SC-005 · 设备绑定与驾驶舱展示

- **关联功能点**：U-060/061（+web 驾驶舱）
- **主流程描述**：取绑定码 → 设备列表 → 进驾驶舱验证飞机 SN
- **前置条件**：司空 PaaS 已有真实 dock 8UUXN2K00A0144

| 步骤 | 操作描述 | 输入数据 | 预期结果 | 实际结果 | 通过 |
|------|----------|----------|----------|----------|:----:|
| 1 | GET /api/v1/bind-codes/current | — | 返回 active 绑定码 | | |
| 2 | GET /paas-api/.../manager/devices | — | 返回 dock 列表 | | |
| 3 | 校验飞行器 SN 映射 | dock 行 | droneSn=1581F8HGX...（非空） | | |
| 4 | 前端进驾驶舱详情 | gatewaySn+droneSn | 正常加载，展示飞机状态 | | |

---

## 冒烟结果汇总

| 编号 | 名称 | 主流程 | 结果 | 缺陷 | 备注 |
|------|------|--------|:----:|------|------|
| SC-001 | 超管登录全链路 | 登录/登出 | ✅ 通过 | 已修复1项（见下） | 首轮暴露“logout 后无法重新登录”缺陷，修复后通过 |
| SC-002 | 建航线并发布 | 航线 CRUD | ✅ 通过 | 无 | aircraftModel 为必填，文档已补 |
| SC-003 | 立即飞行任务 | 任务生命周期 | ⏸ 未执行 | — | 会真实下发任务至机场/真机，需飞行员确认后单独执行 |
| SC-004 | 媒体上传倒序 | 媒体库 | ◐ 部分 | 无 | 列表接口只读验证通过；上传/直传分支未执行 |
| SC-005 | 设备绑定驾驶舱 | 设备/驾驶舱 | ◐ 部分 | 无 | 绑定码+设备列表（3 台）只读验证通过；驾驶舱前端页面未验 |

**冒烟结论**：通过（API 层）。执行时间 2026-09-20，脚本 `scripts/smoke_api.sh`（WSL 内执行），PASS=13 FAIL=0。

**缺陷与修复留痕**：
1. 【产品缺陷·已修复】JWT 无 jti/iat，同账号签发同一 token；logout 将 token 加入黑名单后，login 仅清用户级吊销不清 token 级，导致登出后无法重新登录（40100）直至重启后端。修复：`AuthService.login` 增加 `tokenBlacklist.remove(token)`；回归单测 `AuthServiceTest.login_afterLogout_shouldUnbanToken`。
2. 【环境对齐】种子账号 admin@aiflow.local 密码与文档不符，已重置为 admin123（BCrypt $2a$）。
3. 【契约偏差·已修文档】SC-002 创建航线 aircraftModel 为必填，输入数据已补。
