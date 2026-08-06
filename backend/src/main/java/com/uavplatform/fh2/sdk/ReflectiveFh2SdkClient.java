package com.uavplatform.fh2.sdk;

import com.uavplatform.fh2.config.Fh2Properties;
import com.uavplatform.fh2.dto.Fh2AircraftLocationVO;
import com.uavplatform.fh2.dto.Fh2CreateFlightTaskCmd;
import com.uavplatform.fh2.dto.Fh2DeviceItemVO;
import com.uavplatform.fh2.dto.Fh2FlightTaskDetailVO;
import com.uavplatform.fh2.dto.Fh2MediaFileVO;
import com.uavplatform.fh2.dto.Fh2MediaPageVO;
import com.uavplatform.fh2.dto.Fh2WaylineItemVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 通过反射调用 com.dji.flighthubsdk.*，避免编译期强依赖 JAR。
 * 将 flight_hub_sdk jar 与 JNI 放到运行时 classpath 后即可生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReflectiveFh2SdkClient implements Fh2SdkClient {

    /** 视为「在飞」的模式码（与 DroneModeCode 枚举名对齐） */
    private static final Set<String> FLYING_MODE_CODES = Set.of(
            "MANUAL_FLIGHT",
            "AUTOMATIC_TAKEOFF",
            "ROUTE_FLIGHT",
            "PANORAMIC_PHOTO",
            "SMART_FOLLOW",
            "ADSB_AVOIDANCE",
            "RETURN_HOME_AUTOMATICALLY",
            "AUTOMATIC_LANDING",
            "FORCED_LANDING",
            "LANDING_WITH_THREE_BLADES",
            "APAS",
            "JOYSTICK",
            "COMMAND_FLIGHT",
            "POI",
            "DEPARTURE_POINT",
            "EXPLORATION_AND_SITING",
            "RELAY_SITING"
    );

    private static final long LOCATION_WAIT_MS = 5000L;

    /** 明确在舱/离线的模式，有坐标也不标为在飞 */
    private static final Set<String> GROUND_MODE_CODES = Set.of(
            "STANDBY",
            "NOT_CONNECTED",
            "UPGRADING",
            "SHUT_DOWN_IN_DOCK",
            "STARTING_UP",
            "NOT_REPORTED",
            "UNKNOWN",
            "RTK_CONVERGING"
    );

    private final Fh2Properties properties;
    private final Fh2RuntimeConfigHolder configHolder;

    private volatile boolean ready;
    private volatile String error;
    private volatile Object workspace;
    /** 多租户 session；getDroneInstance(sessionId, sn) 需要 */
    private volatile Long sessionId;

    @PostConstruct
    public void init() {
        refresh();
    }

    public synchronized void refresh() {
        ready = false;
        workspace = null;
        sessionId = null;
        error = null;
        Fh2Properties cfg = configHolder.effective();
        if (!cfg.isEnabled() || !cfg.isSdkNativeEnabled()) {
            error = "FH2 native SDK disabled";
            return;
        }
        if (!StringUtils.hasText(cfg.getUserToken()) || !StringUtils.hasText(cfg.getBindingUuid())) {
            error = "userToken / bindingUuid 未配置，跳过原生 SDK 初始化";
            log.warn("[FH2] {}", error);
            return;
        }
        try {
            Class.forName("com.dji.flighthubsdk.FlightHubSDK");
        } catch (ClassNotFoundException e) {
            error = "classpath 未找到 flight_hub_sdk（请将 jar 放入 backend/libs/flight-hub-sdk.jar 并重启）";
            log.warn("[FH2] {}", error);
            return;
        }
        try {
            tryLoadJni();
            Object sdkConfig = newInstance("com.dji.flighthubsdk.bean.FlightHubSdkConfig");
            invoke(sdkConfig, "setAddress", String.class, blankTo(cfg.getSdkAddress(), stripPort(cfg.getServerHost())));
            invoke(sdkConfig, "setPort", String.class, blankTo(cfg.getSdkPort(), extractPort(cfg.getServerHost(), "30812")));
            invoke(sdkConfig, "setToken", String.class, cfg.getUserToken());
            invoke(sdkConfig, "setNotificationServerAddress", String.class, cfg.resolvedNotificationServer());
            invoke(sdkConfig, "setLogLevel", String.class, "info");
            invoke(sdkConfig, "setLogToConsole", boolean.class, true);
            invoke(sdkConfig, "setShowVersionInfo", boolean.class, true);
            // 避免默认 ./logs 写到只读目录；WSL/容器下用 /tmp
            invoke(sdkConfig, "setLogFilePath", String.class, System.getProperty("uav.fh2.sdk-log-dir", "/tmp/fh2-sdk-logs"));

            if (StringUtils.hasText(cfg.getExtraHttpHeaderKey())) {
                Object header = newInstance("com.dji.flighthubsdk.bean.HeaderKeyValue",
                        new Class[]{String.class, String.class},
                        cfg.getExtraHttpHeaderKey(), cfg.getExtraHttpHeaderValue());
                List<Object> headers = List.of(header);
                Class<?> sdk = Class.forName("com.dji.flighthubsdk.FlightHubSDK");
                Method setHttp = sdk.getMethod("setHttpHeaders", List.class);
                Method setWs = sdk.getMethod("setWebSocketAuthHeaders", List.class);
                setHttp.invoke(null, headers);
                setWs.invoke(null, headers);
            }

            Class<?> sdk = Class.forName("com.dji.flighthubsdk.FlightHubSDK");
            Object result = sdk.getMethod("initialize", Class.forName("com.dji.flighthubsdk.bean.FlightHubSdkConfig"))
                    .invoke(null, sdkConfig);
            if (!isSuccess(result)) {
                error = "FlightHubSDK.initialize failed: " + result
                        + "（常见原因：WSL 缺少 libatomic1，执行 sudo apt install -y libatomic1 后重启后端）";
                log.error("[FH2] {}", error);
                return;
            }

            // 优先多租户 session
            Object sid = null;
            try {
                Class<?> manager = Class.forName("com.dji.flighthubsdk.core.FlightHubSdkManager");
                sid = manager.getMethod("registerWorkspaceToken", String.class, String.class)
                        .invoke(null, cfg.getBindingUuid(), cfg.getUserToken());
                if (sid instanceof Long && ((Long) sid) != 0L) {
                    sessionId = (Long) sid;
                    workspace = manager.getMethod("getWorkspaceInstance", long.class).invoke(null, sessionId);
                }
            } catch (Exception ex) {
                log.debug("[FH2] registerWorkspaceToken 不可用，回退 getWorkspaceInstance(): {}", ex.getMessage());
            }
            if (workspace == null) {
                workspace = sdk.getMethod("getWorkspaceInstance").invoke(null);
            }
            if (workspace == null) {
                error = "Workspace 为空，请检查 binding_uuid / token";
                return;
            }
            ready = true;
            log.info("[FH2] native SDK initialized, version={}, sessionId={}",
                    safeInvokeStatic("com.dji.flighthubsdk.FlightHubSDK", "getVersion"),
                    sessionId);
        } catch (Exception e) {
            error = "SDK init exception: " + e.getMessage();
            log.error("[FH2] init failed", e);
        }
    }

    @Override
    public boolean available() {
        return ready && workspace != null;
    }

    @Override
    public Optional<String> initError() {
        return Optional.ofNullable(error);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Fh2DeviceItemVO> listDevices() {
        ensureReady();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Fh2DeviceItemVO>> ref = new AtomicReference<>(List.of());
        AtomicReference<String> err = new AtomicReference<>();
        try {
            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$DeviceListCallback");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName()) && args != null && args.length >= 2) {
                    Object errorObj = args[0];
                    Object devices = args[1];
                    if (errorObj != null && !isEmptyError(errorObj)) {
                        err.set(String.valueOf(errorObj));
                    } else if (devices instanceof List<?> list) {
                        ref.set(mapDevices(list));
                    }
                    latch.countDown();
                }
                return null;
            });
            workspace.getClass().getMethod("getDeviceList", cbClass).invoke(workspace, cb);
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("getDeviceList timeout");
            }
            if (err.get() != null) {
                throw new IllegalStateException("getDeviceList: " + err.get());
            }
            return ref.get();
        } catch (Exception e) {
            throw new IllegalStateException("listDevices failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Fh2AircraftLocationVO> listFlyingAircraftLocations() {
        ensureReady();
        return listFlyingAircraftLocations(List.of());
    }

    @Override
    public List<Fh2AircraftLocationVO> listFlyingAircraftLocations(List<Fh2DeviceItemVO> seeds) {
        ensureReady();
        List<Fh2DeviceItemVO> aircraft = resolveAircraftCandidates(seeds == null ? List.of() : seeds);
        log.info("[FH2] flying-location candidates={}", aircraft.stream().map(Fh2DeviceItemVO::getSn).toList());
        if (aircraft.isEmpty()) {
            return List.of();
        }
        int threads = Math.min(8, aircraft.size());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Fh2AircraftLocationVO>> futures = new ArrayList<>(aircraft.size());
            for (Fh2DeviceItemVO item : aircraft) {
                futures.add(pool.submit(() -> fetchFlyingLocation(item)));
            }
            List<Fh2AircraftLocationVO> out = new ArrayList<>();
            for (Future<Fh2AircraftLocationVO> future : futures) {
                try {
                    Fh2AircraftLocationVO vo = future.get(LOCATION_WAIT_MS + 2000L, TimeUnit.MILLISECONDS);
                    if (vo != null) {
                        out.add(vo);
                    }
                } catch (Exception e) {
                    log.debug("[FH2] aircraft location future failed: {}", e.getMessage());
                }
            }
            log.info("[FH2] flying-location result size={}", out.size());
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 从本地种子（机场/飞行器）+ 司空拓扑解析出飞行器 SN。
     * 本地常只有 dock，需 getDeviceDetailInfo 取 subDevices。
     */
    private List<Fh2DeviceItemVO> resolveAircraftCandidates(List<Fh2DeviceItemVO> seeds) {
        java.util.LinkedHashMap<String, Fh2DeviceItemVO> bySn = new java.util.LinkedHashMap<>();
        for (Fh2DeviceItemVO seed : seeds) {
            if (seed == null || !StringUtils.hasText(seed.getSn())) {
                continue;
            }
            String type = blankTo(seed.getDeviceType(), "").toLowerCase();
            if ("aircraft".equals(type) || "drone".equals(type)) {
                bySn.putIfAbsent(seed.getSn(), seed);
                continue;
            }
            // dock / gateway / unknown：查子设备
            for (Fh2DeviceItemVO child : listSubAircraft(seed.getSn())) {
                bySn.putIfAbsent(child.getSn(), child);
            }
        }
        try {
            for (Fh2DeviceItemVO d : listDevices()) {
                if (d == null || !StringUtils.hasText(d.getSn())) {
                    continue;
                }
                String type = blankTo(d.getDeviceType(), "").toLowerCase();
                if ("aircraft".equals(type) || "drone".equals(type)) {
                    bySn.putIfAbsent(d.getSn(), d);
                } else if ("dock".equals(type) || "gateway".equals(type)) {
                    for (Fh2DeviceItemVO child : listSubAircraft(d.getSn())) {
                        bySn.putIfAbsent(child.getSn(), child);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[FH2] listDevices for location candidates failed: {}", e.getMessage());
        }
        return new ArrayList<>(bySn.values());
    }

    private List<Fh2DeviceItemVO> listSubAircraft(String gatewaySn) {
        if (!StringUtils.hasText(gatewaySn)) {
            return List.of();
        }
        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Object> detailRef = new AtomicReference<>();
            AtomicReference<String> err = new AtomicReference<>();
            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$GetDeviceDetailInfoCallback");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName()) && args != null && args.length >= 2) {
                    Object errorObj = args[0];
                    Object detail = args[1];
                    if (errorObj != null && !isEmptyError(errorObj)) {
                        err.set(formatError(errorObj));
                    } else {
                        detailRef.set(detail);
                    }
                    latch.countDown();
                }
                return null;
            });
            workspace.getClass().getMethod("getDeviceDetailInfo", String.class, cbClass)
                    .invoke(workspace, gatewaySn, cb);
            if (!latch.await(10, TimeUnit.SECONDS)) {
                log.warn("[FH2] getDeviceDetailInfo timeout sn={}", gatewaySn);
                return List.of();
            }
            if (err.get() != null) {
                log.warn("[FH2] getDeviceDetailInfo sn={}: {}", gatewaySn, err.get());
                return List.of();
            }
            Object detail = detailRef.get();
            if (detail == null) {
                return List.of();
            }
            Object subs = invoke(detail, "getSubDevices");
            if (!(subs instanceof List<?> list) || list.isEmpty()) {
                return List.of();
            }
            List<Fh2DeviceItemVO> out = new ArrayList<>();
            for (Object sub : list) {
                String sn = str(invoke(sub, "getDeviceSn"));
                if (!StringUtils.hasText(sn)) {
                    continue;
                }
                String model = "unknown";
                Object modelInfo = invoke(sub, "getModelInfo");
                if (modelInfo != null) {
                    model = blankTo(firstText(str(invoke(modelInfo, "getName")), str(invoke(modelInfo, "getKey"))), "unknown");
                }
                out.add(Fh2DeviceItemVO.builder()
                        .sn(sn)
                        .name(sn)
                        .gatewaySn(gatewaySn)
                        .deviceType("aircraft")
                        .model(model)
                        .workStatus("idle")
                        .online(Boolean.TRUE.equals(invoke(sub, "isOnline")))
                        .build());
            }
            return out;
        } catch (Exception e) {
            log.warn("[FH2] listSubAircraft sn={}: {}", gatewaySn, e.getMessage());
            return List.of();
        }
    }

    /**
     * 对单架飞机：订阅位置 + 姿态朝向 + 模式，短时等待后取消；坐标有效且非「在舱」模式则返回。
     */
    private Fh2AircraftLocationVO fetchFlyingLocation(Fh2DeviceItemVO device) {
        Object drone = null;
        AtomicInteger locListenId = new AtomicInteger(-1);
        AtomicInteger modeListenId = new AtomicInteger(-1);
        AtomicInteger attitudeListenId = new AtomicInteger(-1);
        try {
            drone = getDroneInstance(device.getSn());
            if (drone == null) {
                log.warn("[FH2] getDroneInstance null sn={}", device.getSn());
                return null;
            }

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Object> locRef = new AtomicReference<>();
            AtomicReference<String> modeRef = new AtomicReference<>();
            AtomicReference<Double> headingRef = new AtomicReference<>();

            Class<?> locCbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$AircraftLocationCallback");
            Object locCb = Proxy.newProxyInstance(locCbClass.getClassLoader(), new Class[]{locCbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName()) && args != null && args.length >= 1 && args[0] != null) {
                    locRef.set(args[0]);
                    latch.countDown();
                }
                return null;
            });

            Class<?> modeCbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$AircraftModeCodeCallback");
            Object modeCb = Proxy.newProxyInstance(modeCbClass.getClassLoader(), new Class[]{modeCbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName()) && args != null && args.length >= 1 && args[0] != null) {
                    Object modeObj = invoke(args[0], "getModeCode");
                    if (modeObj != null) {
                        modeRef.set(String.valueOf(modeObj));
                    }
                }
                return null;
            });

            Class<?> attCbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$AircraftAttitudeCallback");
            Object attCb = Proxy.newProxyInstance(attCbClass.getClassLoader(), new Class[]{attCbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName()) && args != null && args.length >= 1 && args[0] != null) {
                    Double head = asDouble(invoke(args[0], "getAttitudeHead"));
                    if (head != null) {
                        headingRef.set(normalizeHeading(head));
                    }
                }
                return null;
            });

            Object locIdObj = drone.getClass().getMethod("listenAircraftLocation", locCbClass).invoke(drone, locCb);
            Object modeIdObj = drone.getClass().getMethod("listenAircraftModeCode", modeCbClass).invoke(drone, modeCb);
            Object attIdObj = drone.getClass().getMethod("listenAircraftAttitude", attCbClass).invoke(drone, attCb);
            if (locIdObj instanceof Number n) {
                locListenId.set(n.intValue());
            }
            if (modeIdObj instanceof Number n) {
                modeListenId.set(n.intValue());
            }
            if (attIdObj instanceof Number n) {
                attitudeListenId.set(n.intValue());
            }

            latch.await(LOCATION_WAIT_MS, TimeUnit.MILLISECONDS);
            // 位置已到时再稍等姿态推送，便于机头朝向一起返回
            if (locRef.get() != null && headingRef.get() == null) {
                try {
                    Thread.sleep(400L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            Object loc = locRef.get();
            if (loc == null) {
                log.info("[FH2] no AircraftLocation within {}ms sn={} mode={}",
                        LOCATION_WAIT_MS, device.getSn(), modeRef.get());
                return null;
            }
            Double latitude = asDouble(invoke(loc, "getLatitude"));
            Double longitude = asDouble(invoke(loc, "getLongitude"));
            if (!validCoordinate(latitude, longitude)) {
                log.info("[FH2] invalid coordinate sn={} lat={} lng={}", device.getSn(), latitude, longitude);
                return null;
            }

            String modeCode = modeRef.get();
            if (isGroundMode(modeCode)) {
                log.info("[FH2] skip ground mode sn={} mode={}", device.getSn(), modeCode);
                return null;
            }

            return Fh2AircraftLocationVO.builder()
                    .sn(device.getSn())
                    .name(device.getName())
                    .gatewaySn(device.getGatewaySn())
                    .model(device.getModel())
                    .modeCode(modeCode)
                    .latitude(latitude)
                    .longitude(longitude)
                    .height(asDouble(invoke(loc, "getHeight")))
                    .elevation(asDouble(invoke(loc, "getElevation")))
                    .heading(headingRef.get())
                    .updatedAt(System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.warn("[FH2] fetchFlyingLocation sn={}: {}", device.getSn(), e.getMessage());
            return null;
        } finally {
            cancelListen(drone, "cancelListenAircraftLocation", locListenId.get());
            cancelListen(drone, "cancelListenAircraftModeCode", modeListenId.get());
            cancelListen(drone, "cancelListenAircraftAttitude", attitudeListenId.get());
        }
    }

    /** 归一化到 [0, 360) */
    private static Double normalizeHeading(double heading) {
        double h = heading % 360.0;
        if (h < 0) {
            h += 360.0;
        }
        return h;
    }

    private Object getDroneInstance(String sn) throws Exception {
        if (sessionId != null && sessionId != 0L) {
            Class<?> manager = Class.forName("com.dji.flighthubsdk.core.FlightHubSdkManager");
            return manager.getMethod("getDroneInstance", long.class, String.class)
                    .invoke(null, sessionId, sn);
        }
        Class<?> sdk = Class.forName("com.dji.flighthubsdk.FlightHubSDK");
        return sdk.getMethod("getDroneInstance", String.class).invoke(null, sn);
    }

    private static boolean isGroundMode(String modeCode) {
        if (!StringUtils.hasText(modeCode)) {
            return false;
        }
        String name = modeCode.trim();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            name = name.substring(dot + 1);
        }
        return GROUND_MODE_CODES.contains(name);
    }

    private static boolean isFlyingMode(String modeCode) {
        if (!StringUtils.hasText(modeCode)) {
            return false;
        }
        String name = modeCode.trim();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            name = name.substring(dot + 1);
        }
        return FLYING_MODE_CODES.contains(name);
    }

    private static boolean validCoordinate(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        if (Math.abs(latitude) < 1e-6 && Math.abs(longitude) < 1e-6) {
            return false;
        }
        return Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
    }

    private static void cancelListen(Object drone, String method, int listenId) {
        if (drone == null || listenId < 0) {
            return;
        }
        try {
            drone.getClass().getMethod(method, int.class).invoke(drone, listenId);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static Double asDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    @Override
    public List<Fh2WaylineItemVO> listWaylines(int page, int pageSize) {
        ensureReady();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Fh2WaylineItemVO>> ref = new AtomicReference<>(List.of());
        AtomicReference<String> err = new AtomicReference<>();
        try {
            Object param = newInstance("com.dji.flighthubsdk.bean.GetWaylineListParam");
            invoke(param, "setPage", int.class, Math.max(page, 0));
            invoke(param, "setPageSize", int.class, pageSize <= 0 ? 50 : pageSize);
            invoke(param, "setStartTimestamp", long.class, 0L);
            invoke(param, "setEndTimestamp", long.class, System.currentTimeMillis());

            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$GetWaylineListCallback");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName()) && args != null && args.length >= 2) {
                    Object errorObj = args[0];
                    Object result = args[1];
                    if (errorObj != null && !isEmptyError(errorObj)) {
                        err.set(String.valueOf(errorObj));
                    } else if (result != null) {
                        Object items = invoke(result, "getItems");
                        if (items instanceof List<?> list) {
                            ref.set(mapWaylines(list));
                        }
                    }
                    latch.countDown();
                }
                return null;
            });
            workspace.getClass().getMethod("getWaylineList",
                    Class.forName("com.dji.flighthubsdk.bean.GetWaylineListParam"), cbClass)
                    .invoke(workspace, param, cb);
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("getWaylineList timeout");
            }
            if (err.get() != null) {
                throw new IllegalStateException("getWaylineList: " + err.get());
            }
            return ref.get();
        } catch (Exception e) {
            throw new IllegalStateException("listWaylines failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateWaylineName(String waylineUuid, String name) {
        ensureReady();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> err = new AtomicReference<>();
        try {
            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$UpdateWaylineCallback");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName())) {
                    if (args != null && args.length > 0 && args[0] != null && !isEmptyError(args[0])) {
                        err.set(String.valueOf(args[0]));
                    }
                    latch.countDown();
                }
                return null;
            });
            workspace.getClass().getMethod("updateWayline", String.class, String.class, cbClass)
                    .invoke(workspace, waylineUuid, name, cb);
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("updateWayline timeout");
            }
            if (err.get() != null) {
                throw new IllegalStateException(err.get());
            }
        } catch (Exception e) {
            throw new IllegalStateException("updateWaylineName failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteWaylines(List<String> waylineUuids) {
        ensureReady();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> err = new AtomicReference<>();
        try {
            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$BatchDeleteWaylinesCallback");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName())) {
                    if (args != null && args.length > 0 && args[0] != null && !isEmptyError(args[0])) {
                        err.set(String.valueOf(args[0]));
                    }
                    latch.countDown();
                }
                return null;
            });
            workspace.getClass().getMethod("batchDeleteWaylines", List.class, cbClass)
                    .invoke(workspace, waylineUuids, cb);
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("batchDeleteWaylines timeout");
            }
            if (err.get() != null) {
                throw new IllegalStateException(err.get());
            }
        } catch (Exception e) {
            throw new IllegalStateException("deleteWaylines failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Fh2MediaPageVO listMediaFiles(long page, long pageSize, List<String> fileTypes, String keyword) {
        long p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 50 : (int) Math.min(pageSize, 200);
        int startIndex = (int) ((p - 1) * (long) size);
        Fh2MediaPageVO result = listMediaFilesFromIndex(startIndex, size, fileTypes, keyword);
        result.setPage(p);
        result.setPageSize(size);
        return result;
    }

    @Override
    public Fh2MediaPageVO listMediaFilesFromIndex(long startIndex, long pageSize, List<String> fileTypes, String keyword) {
        ensureReady();
        int size = pageSize <= 0 ? 50 : (int) Math.min(pageSize, 200);
        int index = (int) Math.max(0, startIndex);
        long logicalPage = size <= 0 ? 1 : (index / size) + 1;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Fh2MediaPageVO> ref = new AtomicReference<>(
                Fh2MediaPageVO.builder().list(List.of()).total(0).page(logicalPage).pageSize(size).build());
        AtomicReference<String> err = new AtomicReference<>();
        try {
            Object mediaModule = workspace.getClass().getMethod("getMediaServiceModule").invoke(workspace);
            if (mediaModule == null) {
                throw new IllegalStateException("MediaServiceModule is null");
            }

            Object param = newInstance("com.dji.flighthubsdk.bean.GetMediaFilesListParams");
            invoke(param, "setFlightId", String.class, "");
            invoke(param, "setStartIndex", int.class, index);
            invoke(param, "setPageSize", int.class, size);
            List<String> types = (fileTypes == null || fileTypes.isEmpty())
                    ? List.of("2", "3")
                    : fileTypes;
            invoke(param, "setFileTypes", List.class, types);
            invoke(param, "setDeviceSn", String.class, "");
            // 司空约定：-1 表示不按时间过滤
            invoke(param, "setStartCreateTime", long.class, -1L);
            invoke(param, "setEndCreateTime", long.class, -1L);

            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$GetMediaFilesListCallback");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName()) && args != null && args.length >= 2) {
                    Object errorObj = args[0];
                    Object result = args[1];
                    if (errorObj != null && !isEmptyError(errorObj)) {
                        err.set(String.valueOf(errorObj));
                    } else if (result != null) {
                        Object listObj = invoke(result, "getList");
                        Object totalObj = invoke(result, "getTotal");
                        List<Fh2MediaFileVO> mapped = listObj instanceof List<?> raw
                                ? mapMediaFiles(raw)
                                : List.of();
                        if (StringUtils.hasText(keyword)) {
                            String kw = keyword.trim().toLowerCase();
                            mapped = mapped.stream()
                                    .filter(m -> m.getFileName() != null
                                            && m.getFileName().toLowerCase().contains(kw))
                                    .toList();
                        }
                        long total = totalObj instanceof Number n ? n.longValue() : mapped.size();
                        ref.set(Fh2MediaPageVO.builder()
                                .list(mapped)
                                .total(total)
                                .page(logicalPage)
                                .pageSize(size)
                                .build());
                    }
                    latch.countDown();
                }
                return null;
            });
            mediaModule.getClass().getMethod(
                            "getMediaFilesList",
                            Class.forName("com.dji.flighthubsdk.bean.GetMediaFilesListParams"),
                            cbClass)
                    .invoke(mediaModule, param, cb);
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("getMediaFilesList timeout");
            }
            if (err.get() != null) {
                throw new IllegalStateException("getMediaFilesList: " + err.get());
            }
            return ref.get();
        } catch (Exception e) {
            throw new IllegalStateException("listMediaFiles failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getMediaDownloadUrl(long fileId) {
        ensureReady();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> urlRef = new AtomicReference<>();
        AtomicReference<String> err = new AtomicReference<>();
        try {
            Object mediaModule = workspace.getClass().getMethod("getMediaServiceModule").invoke(workspace);
            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$GetMediaFilesDownloadUrlCallback");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName()) && args != null && args.length >= 2) {
                    Object errorObj = args[0];
                    Object result = args[1];
                    if (errorObj != null && !isEmptyError(errorObj)) {
                        err.set(String.valueOf(errorObj));
                    } else if (result != null) {
                        urlRef.set(str(invoke(result, "getUrl")));
                    }
                    latch.countDown();
                }
                return null;
            });
            mediaModule.getClass().getMethod("getMediaFilesDownloadURL", long.class, cbClass)
                    .invoke(mediaModule, fileId, cb);
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("getMediaFilesDownloadURL timeout");
            }
            if (err.get() != null) {
                throw new IllegalStateException(err.get());
            }
            if (!StringUtils.hasText(urlRef.get())) {
                throw new IllegalStateException("empty download url");
            }
            return urlRef.get();
        } catch (Exception e) {
            throw new IllegalStateException("getMediaDownloadUrl failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteMediaFiles(List<Long> fileIds) {
        ensureReady();
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> err = new AtomicReference<>();
        try {
            Object mediaModule = workspace.getClass().getMethod("getMediaServiceModule").invoke(workspace);
            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$ResultCallbackWithError");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName())) {
                    if (args != null && args.length > 0 && args[0] != null && !isEmptyError(args[0])) {
                        err.set(String.valueOf(args[0]));
                    }
                    latch.countDown();
                }
                return null;
            });
            mediaModule.getClass().getMethod("deleteMediaFiles", List.class, cbClass)
                    .invoke(mediaModule, fileIds, cb);
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("deleteMediaFiles timeout");
            }
            if (err.get() != null) {
                throw new IllegalStateException(err.get());
            }
        } catch (Exception e) {
            throw new IllegalStateException("deleteMediaFiles failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String createFlightTask(Fh2CreateFlightTaskCmd cmd) {
        ensureReady();
        if (cmd == null || !StringUtils.hasText(cmd.getWaylineUuid()) || !StringUtils.hasText(cmd.getSn())) {
            throw new IllegalArgumentException("waylineUuid / sn 必填");
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> uuidRef = new AtomicReference<>();
        AtomicReference<String> err = new AtomicReference<>();
        try {
            Object req = newInstance("com.dji.flighthubsdk.bean.CreateTaskReq");
            invoke(req, "setName", String.class, blankTo(cmd.getName(), "AiFlow-task"));
            invoke(req, "setSn", String.class, cmd.getSn().trim());
            invoke(req, "setTaskType", int.class, cmd.getTaskType());
            invoke(req, "setTimeZone", String.class, blankTo(cmd.getTimeZone(), "Asia/Shanghai"));
            invoke(req, "setWaylineUuid", String.class, cmd.getWaylineUuid().trim());
            invoke(req, "setOutOfControlActionInFlight", int.class,
                    cmd.getOutOfControlActionInFlight() <= 0 ? 1 : cmd.getOutOfControlActionInFlight());
            if (cmd.getRthAltitude() != null) {
                invoke(req, "setRthAltitude", double.class, cmd.getRthAltitude());
            }
            if (cmd.getRthMode() != null) {
                invoke(req, "setRthMode", int.class, cmd.getRthMode());
            }
            if (cmd.getBeginAt() != null) {
                invoke(req, "setBeginAt", Long.class, cmd.getBeginAt());
            }
            if (cmd.getEndAt() != null) {
                invoke(req, "setEndAt", Long.class, cmd.getEndAt());
            }
            // 仅在调用方显式传入时设置可选字段，避免 SDK 默认 0 / 空串触发 200101 param is invalid
            if (cmd.getDeviceType() != null) {
                invoke(req, "setDeviceType", int.class, cmd.getDeviceType());
            }
            if (StringUtils.hasText(cmd.getLandingDockSn())) {
                invoke(req, "setLandingDockSn", String.class, cmd.getLandingDockSn().trim());
            }
            if (cmd.getMinBatteryCapacity() != null) {
                invoke(req, "setMinBatteryCapacity", int.class, cmd.getMinBatteryCapacity());
            }
            if (cmd.getWaylinePrecisionType() != null) {
                invoke(req, "setWaylinePrecisionType", int.class, cmd.getWaylinePrecisionType());
            }
            if (cmd.getBreakPointResume() != null) {
                invoke(req, "setBreakPointResume", boolean.class, cmd.getBreakPointResume());
            }

            log.info("[FH2] createFlightTask req name={} sn={} type={} wayline={} rth={} lost={}",
                    cmd.getName(), cmd.getSn(), cmd.getTaskType(), cmd.getWaylineUuid(),
                    cmd.getRthAltitude(), cmd.getOutOfControlActionInFlight());

            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$CreateFlightTaskCallback");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName()) && args != null && args.length >= 2) {
                    Object errorObj = args[0];
                    Object resp = args[1];
                    if (errorObj != null && !isEmptyError(errorObj)) {
                        err.set(formatError(errorObj));
                    } else if (resp != null) {
                        uuidRef.set(str(invoke(resp, "getUuid")));
                    }
                    latch.countDown();
                }
                return null;
            });

            Object module = flightTaskModule();
            module.getClass()
                    .getMethod("createFlightTask", Class.forName("com.dji.flighthubsdk.bean.CreateTaskReq"), cbClass)
                    .invoke(module, req, cb);
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("createFlightTask timeout");
            }
            if (err.get() != null) {
                throw new IllegalStateException("createFlightTask: " + err.get());
            }
            if (!StringUtils.hasText(uuidRef.get())) {
                throw new IllegalStateException("createFlightTask 未返回 uuid");
            }
            // Apifox 文档另有 executeFlightTask；当前 jar 无此方法时跳过，立即任务由云端在 create 后触发
            tryExecuteFlightTaskIfPresent(module, uuidRef.get());
            log.info("[FH2] createFlightTask ok uuid={} type={}", uuidRef.get(), cmd.getTaskType());
            return uuidRef.get().trim();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("createFlightTask failed: " + rootMessage(e), e);
        }
    }

    @Override
    public Fh2FlightTaskDetailVO getFlightTaskDetail(String taskUuid) {
        ensureReady();
        if (!StringUtils.hasText(taskUuid)) {
            throw new IllegalArgumentException("taskUuid 必填");
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Fh2FlightTaskDetailVO> ref = new AtomicReference<>();
        AtomicReference<String> err = new AtomicReference<>();
        try {
            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$GetFlightTaskDetailCallback");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName()) && args != null && args.length >= 2) {
                    Object errorObj = args[0];
                    Object detail = args[1];
                    if (errorObj != null && !isEmptyError(errorObj)) {
                        err.set(formatError(errorObj));
                    } else if (detail != null) {
                        ref.set(Fh2FlightTaskDetailVO.builder()
                                .uuid(str(invoke(detail, "getUuid")))
                                .name(str(invoke(detail, "getName")))
                                .taskType(asInt(invoke(detail, "getTaskType")))
                                .status(asInt(invoke(detail, "getStatus")))
                                .sn(str(invoke(detail, "getSn")))
                                .landingDockSn(str(invoke(detail, "getLandingDockSn")))
                                .waylineUuid(str(invoke(detail, "getWaylineUuid")))
                                .beginAt(str(invoke(detail, "getBeginAt")))
                                .endAt(str(invoke(detail, "getEndAt")))
                                .runAt(str(invoke(detail, "getRunAt")))
                                .completedAt(str(invoke(detail, "getCompletedAt")))
                                .build());
                    }
                    latch.countDown();
                }
                return null;
            });
            Object module = flightTaskModule();
            module.getClass().getMethod("getFlightTaskDetail", String.class, cbClass)
                    .invoke(module, taskUuid.trim(), cb);
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("getFlightTaskDetail timeout");
            }
            if (err.get() != null) {
                throw new IllegalStateException("getFlightTaskDetail: " + err.get());
            }
            return ref.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("getFlightTaskDetail failed: " + rootMessage(e), e);
        }
    }

    @Override
    public void deleteFlightTask(String taskUuid) {
        ensureReady();
        if (!StringUtils.hasText(taskUuid)) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> err = new AtomicReference<>();
        try {
            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$ResultCallbackWithError");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName())) {
                    if (args != null && args.length > 0 && args[0] != null && !isEmptyError(args[0])) {
                        err.set(formatError(args[0]));
                    }
                    latch.countDown();
                }
                return null;
            });
            Object module = flightTaskModule();
            module.getClass().getMethod("deleteFlightTask", String.class, cbClass)
                    .invoke(module, taskUuid.trim(), cb);
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("deleteFlightTask timeout");
            }
            if (err.get() != null) {
                throw new IllegalStateException("deleteFlightTask: " + err.get());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("deleteFlightTask failed: " + rootMessage(e), e);
        }
    }

    private Object flightTaskModule() throws Exception {
        Object module = workspace.getClass().getMethod("getFlightTaskModule").invoke(workspace);
        if (module == null) {
            throw new IllegalStateException("getFlightTaskModule 返回空");
        }
        return module;
    }

    /** 若 SDK 后续版本提供 executeFlightTask，则创建后主动下发执行。 */
    private void tryExecuteFlightTaskIfPresent(Object module, String taskUuid) {
        try {
            Class<?> cbClass = Class.forName("com.dji.flighthubsdk.FlightHubCallback$ResultCallbackWithError");
            Method exec = findMethod(module.getClass(), "executeFlightTask", String.class, cbClass);
            if (exec == null) {
                return;
            }
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> err = new AtomicReference<>();
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class[]{cbClass}, (proxy, method, args) -> {
                if ("onResult".equals(method.getName())) {
                    if (args != null && args.length > 0 && args[0] != null && !isEmptyError(args[0])) {
                        err.set(formatError(args[0]));
                    }
                    latch.countDown();
                }
                return null;
            });
            exec.invoke(module, taskUuid, cb);
            if (!latch.await(30, TimeUnit.SECONDS)) {
                log.warn("[FH2] executeFlightTask timeout uuid={}", taskUuid);
                return;
            }
            if (err.get() != null) {
                throw new IllegalStateException("executeFlightTask: " + err.get());
            }
            log.info("[FH2] executeFlightTask ok uuid={}", taskUuid);
        } catch (ClassNotFoundException e) {
            // ignore
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.debug("[FH2] executeFlightTask skip: {}", e.getMessage());
        }
    }

    private void ensureReady() {
        if (!available()) {
            throw new IllegalStateException(error == null ? "FH2 SDK not ready" : error);
        }
    }

    private List<Fh2MediaFileVO> mapMediaFiles(List<?> items) {
        List<Fh2MediaFileVO> out = new ArrayList<>();
        for (Object item : items) {
            String fileName = str(invoke(item, "getFileName"));
            String suffix = str(invoke(item, "getSuffix"));
            out.add(Fh2MediaFileVO.builder()
                    .fileId(asLong(invoke(item, "getFileId")))
                    .fileName(fileName)
                    .suffix(suffix)
                    .objectKey(str(invoke(item, "getObjectKey")))
                    .filePath(str(invoke(item, "getFilePath")))
                    .sizeBytes(asLong(invoke(item, "getSize")))
                    .deviceSn(str(invoke(item, "getDeviceSn")))
                    .flightId(str(invoke(item, "getFlightId")))
                    .payloadType(str(invoke(item, "getPayloadType")))
                    .createdAt(str(invoke(item, "getCreatedAt")))
                    .mediaType(guessMediaType(suffix, fileName))
                    .build());
        }
        return out;
    }

    private static String guessMediaType(String suffix, String fileName) {
        String ext = blankTo(suffix, "").toLowerCase().replace(".", "");
        if (!StringUtils.hasText(ext) && StringUtils.hasText(fileName) && fileName.contains(".")) {
            ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        }
        if (List.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "tif", "tiff", "dng").contains(ext)) {
            return "image";
        }
        if (List.of("mp4", "mov", "avi", "mkv", "wmv", "flv", "m4v", "webm", "3gp", "ts").contains(ext)) {
            return "video";
        }
        return "unknown";
    }

    private List<Fh2DeviceItemVO> mapDevices(List<?> devices) {
        List<Fh2DeviceItemVO> out = new ArrayList<>();
        for (Object topo : devices) {
            Object main = invoke(topo, "getMainDevice");
            if (main == null) {
                continue;
            }
            String gatewaySn = str(invoke(main, "getDeviceSn"));
            out.add(Fh2DeviceItemVO.builder()
                    .sn(gatewaySn)
                    .name(blankTo(str(invoke(main, "getDeviceName")), gatewaySn))
                    .deviceType(guessType(str(invoke(main, "getDeviceType")), true))
                    .model(blankTo(str(invoke(main, "getDeviceModel")), "unknown"))
                    .gatewaySn(null)
                    .workStatus("idle")
                    .online(true)
                    .build());
            Object subs = invoke(topo, "getSubDevices");
            if (subs instanceof List<?> subList) {
                for (Object sub : subList) {
                    String sn = str(invoke(sub, "getDeviceSn"));
                    out.add(Fh2DeviceItemVO.builder()
                            .sn(sn)
                            .name(blankTo(str(invoke(sub, "getDeviceName")), sn))
                            .deviceType("aircraft")
                            .model(blankTo(str(invoke(sub, "getDeviceModel")), "unknown"))
                            .gatewaySn(gatewaySn)
                            .workStatus("idle")
                            .online(true)
                            .build());
                }
            }
        }
        return out;
    }

    private List<Fh2WaylineItemVO> mapWaylines(List<?> items) {
        List<Fh2WaylineItemVO> out = new ArrayList<>();
        for (Object item : items) {
            out.add(Fh2WaylineItemVO.builder()
                    .waylineUuid(str(invoke(item, "getWaylineUuid")))
                    .waylineName(firstText(
                            str(invoke(item, "getWaylineName")),
                            str(invoke(item, "getName")),
                            str(invoke(item, "getTitle"))))
                    .droneType(String.valueOf(invoke(item, "getDroneType")))
                    .size(asLong(invoke(item, "getSize")))
                    .updatedAt(asLong(invoke(item, "getUpdatedAt")))
                    .aiWayline(Boolean.TRUE.equals(invoke(item, "isAiWayline")))
                    .build());
        }
        return out;
    }

    private static String guessType(String raw, boolean gateway) {
        if (raw == null) {
            return gateway ? "dock" : "aircraft";
        }
        String s = raw.toLowerCase();
        if (s.contains("drone") || s.contains("aircraft")) {
            return "aircraft";
        }
        if (s.contains("dock") || s.contains("airport") || s.contains("gateway")) {
            return "dock";
        }
        return gateway ? "dock" : "other";
    }

    private static void tryLoadJni() {
        try {
            Class<?> loader = Class.forName("com.dji.flighthubsdk.core.JniLibraryLoader");
            loader.getMethod("loadLibrary").invoke(null);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String msg = root.getMessage() == null ? e.getMessage() : root.getMessage();
            // 不吞掉：让上层 initialize 或 refresh 能带上真实缺库信息
            throw new IllegalStateException("JNI load failed: " + msg
                    + (msg != null && msg.contains("libatomic")
                    ? " → 在 WSL 执行: sudo apt install -y libatomic1"
                    : ""), e);
        }
    }

    private static boolean isSuccess(Object result) {
        if (result == null) {
            return false;
        }
        String name = result.toString();
        return "SUCCESS".equalsIgnoreCase(name) || name.endsWith(".SUCCESS");
    }

    private static boolean isEmptyError(Object errorObj) {
        try {
            Object success = invoke(errorObj, "isSuccess");
            if (success instanceof Boolean b) {
                return b;
            }
            Object code = invoke(errorObj, "getCode");
            if (code == null) {
                code = invoke(errorObj, "getErrorCode");
            }
            if (code == null) {
                return true;
            }
            String s = String.valueOf(code);
            return "0".equals(s) || "SUCCESS".equalsIgnoreCase(s) || s.endsWith(".SUCCESS");
        } catch (Exception e) {
            return false;
        }
    }

    private static String formatError(Object errorObj) {
        if (errorObj == null) {
            return "unknown";
        }
        try {
            String msg = str(invoke(errorObj, "getServerMessage"));
            Object code = invoke(errorObj, "getErrorCode");
            Object serverCode = invoke(errorObj, "getServerCode");
            Object httpCode = invoke(errorObj, "getHttpCode");
            StringBuilder sb = new StringBuilder();
            if (code != null) {
                sb.append(code);
            }
            if (serverCode != null) {
                if (!sb.isEmpty()) {
                    sb.append('/');
                }
                sb.append("server=").append(serverCode);
            }
            if (httpCode != null) {
                if (!sb.isEmpty()) {
                    sb.append('/');
                }
                sb.append("http=").append(httpCode);
            }
            if (StringUtils.hasText(msg)) {
                if (!sb.isEmpty()) {
                    sb.append(": ");
                }
                sb.append(msg);
            }
            return sb.isEmpty() ? String.valueOf(errorObj) : sb.toString();
        } catch (Exception e) {
            return String.valueOf(errorObj);
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (StringUtils.hasText(msg)) {
            return msg;
        }
        return root.getClass().getSimpleName();
    }

    private static Object newInstance(String className) throws Exception {
        return Class.forName(className).getDeclaredConstructor().newInstance();
    }

    private static Object newInstance(String className, Class<?>[] types, Object... args) throws Exception {
        Constructor<?> c = Class.forName(className).getConstructor(types);
        return c.newInstance(args);
    }

    private static Object invoke(Object target, String method, Class<?> type, Object arg) throws Exception {
        return target.getClass().getMethod(method, type).invoke(target, arg);
    }

    private static Object invoke(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object safeInvokeStatic(String className, String method) {
        try {
            return Class.forName(className).getMethod(method).invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private static String blankTo(String v, String def) {
        return StringUtils.hasText(v) ? v : def;
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value) && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return null;
    }

    private static String stripPort(String host) {
        if (!StringUtils.hasText(host)) {
            return host;
        }
        try {
            java.net.URI uri = java.net.URI.create(host);
            if (uri.getScheme() != null && uri.getHost() != null) {
                return uri.getScheme() + "://" + uri.getHost();
            }
        } catch (Exception ignored) {
        }
        return host.replaceAll(":\\d+$", "");
    }

    private static String extractPort(String host, String def) {
        try {
            java.net.URI uri = java.net.URI.create(host);
            if (uri.getPort() > 0) {
                return String.valueOf(uri.getPort());
            }
        } catch (Exception ignored) {
        }
        return def;
    }
}
