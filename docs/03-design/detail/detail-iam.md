# 详细设计 · IAM（U-001～U-004、U-087）· SDD

| 文档版本 | 修改日期 | 修改内容 |
|----------|----------|----------|
| V2.0 | 2026-08-24 | 按已实现代码补齐（V1.0 空缺）；新增 U-087 个人信息中心 |

**涉及表**：`t_user`（含 `email` 增量列）、`t_role`、`t_role_permission`、`t_permission`

**域错误码**

| 错误码 | 说明 |
|--------|------|
| 10001 | 账号不存在或密码错误 |
| 10002 | 账号已禁用 |
| 10003 | 账号已存在 |
| 10004 | 角色不存在 |
| 10005 | 旧密码错误 |
| 10006 | 系统预置角色不可修改 |

---

## 公共：LoginResponse / UserVO

**LoginResponse**：`accessToken`(string), `expiresIn`(long 秒), `user`(UserVO)

**UserVO**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | |
| account | string | |
| displayName | string | |
| email | string | 可空 |
| roleCode / roleName | string | |
| permissions | string[] | 权限码列表（前端菜单渲染依据） |

---

## U-001 · 账号密码登录

**优先级**：P0 · **权限**：匿名

### 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/login` | 登录 |
| POST | `/api/v1/auth/logout` | 登出 |
| GET | `/api/v1/auth/me` | 当前用户 |

**LoginRequest**：`account`(必填), `password`(必填)

### 实现要点

1. 密码 BCrypt 校验；失败统一 `10001`（不区分账号/密码错）。
2. 签发 JWT（`JwtTokenProvider`），claims 含 userId/roleCode/权限码快照。
3. 登出将 token 加入内存 `TokenBlacklist`；删人时按 userId 批量吊销（`revokeUser`）。
4. 前端将 accessToken 存 Pinia + localStorage；路由守卫拦截未登录跳 `/login?redirect=`。
5. 兼容头：`X-User-Token: <jwt>` 与 `Authorization: Bearer <jwt>` 等效（AuthInterceptor）。

---

## U-002 · 人员添加

**优先级**：P0 · **权限**：`member:write`

### 接口 `POST /api/v1/members`

**MemberCreateRequest**：`account`(必填,唯一), `displayName`(必填), `password`(必填), `roleId`(必填), `joinMethod`(可选)

响应：`IdVO`

规则：账号重复 → `10003`；角色不存在 → `10004`。

---

## U-003 · 角色权限配置

**优先级**：P0 · **权限**：`role:manage`

### 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/roles` | 角色列表（含权限码） |
| GET | `/api/v1/roles/options` | 下拉选项（精简） |
| GET | `/api/v1/roles/{id}` | 角色详情 |
| PUT | `/api/v1/roles/{id}` | 更新角色权限 |
| GET | `/api/v1/permissions` | 权限点目录 |

**RoleVO**：id, code, name, description, isSystem, locked, permissionIds, permissionCodes

**RoleUpdateRequest**：`name`(可选), `description`(可选), `permissionIds`(必填)

规则：`locked=1` 的系统预置角色不可改 → `10006`；权限变更仅影响后续签发的 JWT（已在线用户权限码快照不变，重新登录生效）。

---

## U-004 · 人员编辑与删除

**优先级**：P1 · **权限**：`member:write`

### 接口 `PUT /api/v1/members/{id}`、`DELETE /api/v1/members/{id}`

**MemberUpdateRequest**：`displayName`, `roleId`, `status`(active/disabled)

**MemberVO**（列表/详情）：id, account, displayName, roleId, roleName, joinMethod, joinedAt, status

列表 `GET /api/v1/members` 筛选：roleId, joinMethod, keyword, page/pageSize。

规则：

1. 删除为逻辑删除，同时 `TokenBlacklist.revokeUser(userId)` 即时踢下线。
2. 禁用（status=disabled）同样吊销存量 token。
3. 不允许删除自己；前端二次确认。

---

## U-087 · 个人信息中心

**优先级**：P1 · **权限**：登录即可（操作本人数据）

### 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/api/v1/auth/email` | 修改当前用户邮箱 |
| PUT | `/api/v1/auth/password` | 修改当前用户密码 |

**UpdateEmailRequest**：`email`(必填，格式校验)
**ChangePasswordRequest**：`oldPassword`(必填), `newPassword`(必填)

规则：旧密码不符 → `10005`；改密成功后吊销当前 token 之外的所有该用户 token（含当前，前端引导重新登录）。

前端 `/profile` 页：基本信息展示 + 邮箱维护 + 密码修改（校验两次输入一致）。
