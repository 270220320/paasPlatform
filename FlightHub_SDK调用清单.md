# FlightHub Java SDK 调用清单

> 项目：UAVPlatform（d:\AiFlow）
> SDK：`com.dji:flight-hub-sdk:2.0.4`（JAR 位于 `backend/libs/flight-hub-sdk.jar`，system scope 引入）
> 导出时间：2026-09-03
>
> 调用方式分两种：
> 1. **反射调用** —— `ReflectiveFh2SdkClient.java`（约 2400 行）：`Class.forName` + 动态代理回调，避免编译期强依赖 JAR；
> 2. **编译期强类型** —— `DroneControlGateway.java`：直接 `import com.dji.flighthubsdk.generated.*`（Wrapper 系列回调是具体类，无法用 Proxy，只能编译期子类化），供 AI 对话飞控使用。

---

## 一、SDK 核心与初始化（FlightHubSDK / FlightHubSdkManager）

| SDK API | 功能 | 调用位置（ReflectiveFh2SdkClient.java） |
|---|---|---|
| `Class.forName("com.dji.flighthubsdk.FlightHubSDK")` | classpath 探测 | L123 |
| `FlightHubSdkConfig`（setAddress / setPort / setToken / setNotificationServerAddress / setLogLevel / setLogToConsole / setShowVersionInfo / setLogFilePath / setLogMaxFileSize / setLogMaxFiles） | SDK 初始化配置 | L131-149 |
| `HeaderKeyValue` | 网关鉴权头 bean | L158 |
| `FlightHubSDK.setWebSocketAuthHeaders(List)` | WebSocket 鉴权头注入（KOC3 起替代 setToken） | L162 |
| `FlightHubSDK.setHttpHeaders(List)` | HTTP 鉴权头注入 | L163 |
| `FlightHubSDK.initialize(FlightHubSdkConfig)` | SDK 初始化 | L172 |
| `FlightHubSdkManager.registerWorkspaceToken(binding, token)` | 多租户组织 binding 注册 | L190 |
| `FlightHubSdkManager.registerWorkspaceToken(project, token)` | 项目工作空间注册（Terra 媒体解析） | L205 |
| `FlightHubSdkManager.getWorkspaceInstance(long sessionId)` | 按 session 取 Workspace | L194 / L209 |
| `FlightHubSdkManager.getWorkspaceInstance(String project)` | 按项目名取 Workspace | L218 |
| `FlightHubSDK.getWorkspaceInstance()` | 默认 Workspace 兜底 | L231 |
| `FlightHubSDK.getVersion()` | 版本探查 | L239 |
| `FlightHubSdkManager.getDroneInstance(sessionId, sn)` | 多租户下取 IDrone | L621-623 |
| `FlightHubSDK.getDroneInstance(sn)` | 默认空间取 IDrone | L625-626 |
| `JniLibraryLoader.loadLibrary()` | JNI 原生库加载（WSL 缺 libatomic1 会失败） | L2240-2241 |

## 二、设备管理 DeviceManageModule

| SDK API | 功能 | 调用位置 |
|---|---|---|
| `getDeviceList(DeviceListCallback)` | 设备拓扑列表（DeviceTopo，主设备+子设备） | L281-298 |
| `getDeviceDetailInfo(sn, GetDeviceDetailInfoCallback)` | 设备详情，取 subDevices 解析 dock 下挂飞行器 | L404-420 |

## 三、OSD 实时遥测（IDroneOsdModule / IDrone，listen 与 cancel 严格配对）

| SDK API | 功能 | 调用位置 |
|---|---|---|
| `listenAircraftLocation` / `cancelListenAircraftLocation` | 实时经纬度/高度/海拔 | L517 / L567 |
| `listenAircraftModeCode` / `cancelListenAircraftModeCode` | 飞行模式码（判定在飞/在舱/离线） | L518 / L568 |
| `listenAircraftAttitude` / `cancelListenAircraftAttitude` | 姿态（机头朝向归一化 0~360） | L519 / L569 |
| `getDroneOsdModule()` | 获取 OSD 模块宿主（3.1.6.0+ 优先 Module，旧版回退 drone） | L596-608 |

> 兼容策略：`invokeListen` / `cancelListen` 优先 `IDroneOsdModule` + `Wrapper` 后缀方法名，旧版回退 IDrone 本身（L577-593 / L663-680）。

## 四、航线管理 WaylineManageModule

| SDK API | 功能 | 调用位置 |
|---|---|---|
| `GetWaylineListParam`（setPage / setPageSize / setStartTimestamp / setEndTimestamp） | 航线查询参数 | L696-700 |
| `getWaylineList(param, GetWaylineListCallback)` | 航线分页列表（含 waylineTypes/aiWayline） | L719-722 |
| `updateWayline(uuid, name, UpdateWaylineCallback)` | 航线重命名 | L751-753 |
| `batchDeleteWaylines(List, BatchDeleteWaylinesCallback)` | 航线批量删除 | L781-783 |

## 五、媒体服务 MediaServiceModule

| SDK API | 功能 | 调用位置 |
|---|---|---|
| `getMediaServiceModule()` | 获取媒体模块 | L829 / L903 / L944 |
| `GetMediaFilesListParams`（setFlightId / setStartIndex / setPageSize / setFileTypes / setDeviceSn / setStartCreateTime / setEndCreateTime） | 媒体查询参数（-1 表示不按时间过滤） | L834-845 |
| `getMediaFilesList(param, GetMediaFilesListCallback)` | 媒体分页列表（fileId/objectKey/flightId 等） | L879-883 |
| `getMediaFilesDownloadURL(fileId, GetMediaFilesDownloadUrlCallback)` | 媒体下载地址 | L918-919 |
| `deleteMediaFiles(List, ResultCallbackWithError)` | 批量删除媒体 | L955-956 |

## 六、飞行任务 FlightTaskModule

| SDK API | 功能 | 调用位置 |
|---|---|---|
| `getFlightTaskModule()` | 获取任务模块 | L1506 |
| `CreateTaskReq`（setName / setSn / setTaskType / setTimeZone / setWaylineUuid / setOutOfControlActionInFlight / setRthAltitude / setRthMode / setBeginAt / setEndAt / setDeviceType / setLandingDockSn / setMinBatteryCapacity / setWaylinePrecisionType / setBreakPointResume） | 创建任务参数（可选字段仅在显式传入时设置，避免 200101） | L1337-1372 |
| `createFlightTask(req, CreateFlightTaskCallback)` | 创建航线任务（立即/条件），返回 taskUuid | L1393-1396 |
| `executeFlightTask(uuid, ResultCallbackWithError)` | 探测性调用：当前 jar 无此方法则跳过，立即任务由云端触发 | L2055-2089 |
| `getFlightTaskDetail(uuid, GetFlightTaskDetailCallback)` | 任务详情（状态/起止时间/航线） | L1453-1455 |
| `deleteFlightTask(uuid, ResultCallbackWithError)` | 删除任务 | L1489-1491 |

## 七、三维重建 TerraBuildModule（私有化建图）

| SDK API | 功能 | 调用位置 |
|---|---|---|
| `getTerraBuildModule()` | 获取建图模块 | L1514 / L1560 |
| `TerraBuildCreateReconstructionParams` + `TerraBuildParameter3D` + `TerraBuildGeoDesc` | 重建参数（对齐 demo 3D：reconstructionType=[3]、qualityLevel、wkt 默认 EPSG:32650、simplifiedFactor 0.8、sfmMethod GLOBAL、useImagePosition、outputMesh、generateB3dm、generateOsgb、generateModelFormats=[1,2]） | L1729-1780 |
| `createReconstruction(params, TerraBuildCreateReconstructionCallback)` | 提交 3D 重建，返回模型数字 ID | L1696-1697 |
| `listenReconstructionStatusChanged(TerraBuildReconstructionStatusChangedCallback)` | 重建状态推送订阅（全局注册一次，缓存错误码） | L1527-1538 |
| `cancelListenReconstructionStatusChanged(listenId)` | 取消状态订阅 | L1562-1563 |
| `getModelFileList(page, size, TerraBuildGetModelFileListCallback)` | 模型分页列表（state/thumbnailUrl/zipUrl） | L1827-1828 |
| `getModelFileUrl(fileId, type, TerraBuildGetModelFileUrlCallback)` | 模型文件下载地址（type 1=原始zip 等） | L2015-2016 |
| `getReconstructionSettings(modelUuid, TerraBuildGetReconstructionSettingsCallback)` | 反查 folderId→folderUuid 映射（含 mediaInputObjectKey 诊断） | L1893-1894 |
| `getReconstructionImages(modelUuid, TerraBuildGetReconstructionImagesCallback)` | 查询重建引擎实际收到的图像数（失败诊断） | L1971-1972 |

## 八、安全服务 SafetyServiceModule（自定义飞行区 / 限飞区）

| SDK API | 功能 | 调用位置 |
|---|---|---|
| `getSafetyServiceModule()` | 获取安全服务模块 | L1306 |
| `CreateCustomizedFlightAreasParams` + `FlightAreasContentData` + `ContentsPropertyData` + `ContentsGeometryData` | 限飞区参数（GeoJSON Feature → SDK bean） | L980-985 / L1241-1260 |
| `createCustomizedFlightAreas(params, CreateCustomizedFlightAreasCallback)` | 创建限飞区，返回 flightAreaId | L1004-1007 |
| `UpdateCustomizedFlightAreasParams` + `updateCustomizedFlightAreas(...)` | 更新限飞区 | L1036-1060 |
| `deleteCustomizedFlightAreas(flightAreaId, ResultCallbackWithError)` | 删除限飞区 | L1097-1099 |
| `syncCustomizedFlightAreas(sns, SyncCustomizedFlightAreasCallback)` | 限飞区下发到机场（返回 [{deviceSn, code}]） | L1153-1155 |
| `getFlightAreasSyncStatus(sns, GetFlightAreasSyncStatusCallback)` | 查询机场同步状态（[{deviceSn, status, reason}]） | L1214-1216 |

## 九、飞控指令（DroneControlGateway.java，编译期强类型，AI 对话 Agent 工具）

| SDK 模块 | SDK API | 功能 | 调用位置 |
|---|---|---|---|
| ControlAuthModule | `acquireFlightControlAuthorityWrapper(ResultCallbackWithError)` | 夺取飞行控制权 | L118 |
| ControlAuthModule | `releaseFlightControlAuthorityWrapper(...)` | 释放飞行控制权（会话层补偿释放） | L133 |
| DrcModule | `connectDrcLinkWrapper(ResultCallback)` | 建立 DRC 低延迟链路 | L148 |
| DrcModule | `disconnectDrcLinkWrapper(...)` | 断开 DRC 链路 | L163 |
| TakeoffModule | `takeoffWrapper(TakeoffParams, TakeoffResultCallback)` | 定点起飞（返回 flight_id） | L181-204 |
| FlightControlModule | `returnHomeWrapper(...)` | 一键返航 | L211 |
| FlightControlModule | `returnHomeCancelWrapper(...)` | 取消返航 | L227 |
| FlightControlModule | `drcEmergencyStopWrapper(...)` | 紧急停止（原地悬停） | L243 |
| FlyToPointModule | `startWrapper(FlyToPointParams, FlyToPointStartCallback)` | 指点飞行（需先 acquire + connectDrc） | L260-277 |
| FlyToPointModule | `drcStopWrapper(...)` | 停止指点飞行 | L284 |
| FlightTaskControlModule | `pauseWrapper(...)` | 航线任务暂停 | L301 |
| FlightTaskControlModule | `resumeWrapper(...)` | 航线任务恢复 | L317 |
| CameraControlModule | `takePhotoWrapper(cameraKey, ...)` | 拍照（wide/zoom/ir 镜头） | L335 |
| DroneOsdModule | `listenAircraftBatteryInfoWrapper(AircraftBatteryInfoCallback)` | 订阅电量（容量/剩余飞行时间/返航电量） | L355 |
| DroneOsdModule | `cancelListenAircraftBatteryInfoWrapper(listenId)` | 取消电量订阅（listen/cancel 配对约束） | L381 |
| — | `Fh2SdkClient.getRawDroneInstance(sn)` → 强转 `IDrone` | 从反射门面取裸实例供强类型调用 | Fh2SdkClient.java L27-29 / Gateway L106-111 |

## 十、涉及的 SDK 回调接口（FlightHubCallback$*，动态代理实现）

DeviceListCallback、GetDeviceDetailInfoCallback、AircraftLocationCallback、AircraftModeCodeCallback、AircraftAttitudeCallback、GetWaylineListCallback、UpdateWaylineCallback、BatchDeleteWaylinesCallback、GetMediaFilesListCallback、GetMediaFilesDownloadUrlCallback、ResultCallbackWithError、CreateCustomizedFlightAreasCallback、UpdateCustomizedFlightAreasCallback、SyncCustomizedFlightAreasCallback、GetFlightAreasSyncStatusCallback、CreateFlightTaskCallback、GetFlightTaskDetailCallback、TerraBuildReconstructionStatusChangedCallback、TerraBuildCreateReconstructionCallback、TerraBuildGetModelFileListCallback、TerraBuildGetReconstructionSettingsCallback、TerraBuildGetReconstructionImagesCallback、TerraBuildGetModelFileUrlCallback

## 十一、涉及的 SDK generated 类型（编译期强引用，仅 DroneControlGateway）

FlightHubError、IDrone、TakeoffParams、TakeoffResult、TakeoffResultCallback、FlyToPointParams、FlyToPointStartCallback、ResultCallback、ResultCallbackWithError、AircraftBatteryInfo、AircraftBatteryInfoCallback

---

## 汇总

- 覆盖 **9 大 SDK 模块**、约 **40 个 API**、**23 个回调接口**
- 业务链路：设备看板、实时位置（OSD）、航线管理、媒体库、任务下发、三维重建（TerraBuild）、限飞区同步（SafetyService）、AI 对话飞控（起飞/返航/急停/指点飞行/拍照/电量）
- 两种调用风格并存的原因：常规接口（interface 回调）走反射 + Proxy；Wrapper 系列回调为具体类，只能编译期子类化（DroneControlGateway）
