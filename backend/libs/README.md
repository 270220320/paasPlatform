# FlightHub Java SDK

后端通过反射加载 `com.dji.flighthubsdk.*`。jar：

```
backend/libs/flight-hub-sdk.jar
```

已在 `pom.xml` 以 system 依赖接入（`includeSystemScope=true`）。

## 平台说明

当前 jar **仅含**：

- `lib/linux-x86_64/`
- `lib/macosx-arm64/`

**无 Windows DLL**。在 Windows 本机直接跑后端会出现 JNI 失败、`sdkAvailable=false`。

## 推荐：Docker（Linux）跑后端

在仓库根目录：

```bash
docker compose up --build -d backend
```

- 容器内为 Linux，可加载 jar 内 JNI
- MySQL 仍用宿主机（`host.docker.internal:3306`，见 `application-docker.yml`）
- API：`http://127.0.0.1:8080`

自检：

```bash
# 登录后
GET /api/v1/paas/sdk/status
# 期望 sdkAvailable=true
```

前端继续在 Windows 用 Vite；代理 `/api` 指向本机 8080 即可。

## 备选：WSL2

在 Ubuntu WSL 安装 JDK 21 + Maven，在 WSL 里启动后端；MySQL 可用 Windows 宿主机 IP 或 WSL 内 MySQL。

**必装系统依赖**（否则 `FlightHubSDK.initialize` → `ERROR_INIT_FAILED`，底层缺 `libatomic.so.1`）：

```bash
sudo apt update
sudo apt install -y libatomic1
```

自检 JNI：

```bash
# 缺库时会报 libVolcEngineRTC.so: libatomic.so.1: cannot open shared object file
ldd backend/libs/lib/linux-x86_64/libVolcEngineRTC.so | grep atomic
```

## 配置

`application.yml` / `t_fh2_config`：`user-token`、`binding-uuid`、`sdk-address`、`sdk-port` 等。
