package com.uavplatform.chat.control;

import com.dji.flighthubsdk.FlightHubSDK;
import com.dji.flighthubsdk.core.DeviceDebugModule;
import com.dji.flighthubsdk.core.Dock;
import com.dji.flighthubsdk.generated.FlightHubError;
import com.dji.flighthubsdk.generated.FlyToPointParams;
import com.dji.flighthubsdk.generated.IDrone;
import com.dji.flighthubsdk.generated.ResultCallback;
import com.dji.flighthubsdk.generated.ResultCallbackWithError;
import com.dji.flighthubsdk.generated.TakeoffParams;
import com.dji.flighthubsdk.generated.TakeoffResult;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.fh2.dto.Fh2AircraftLocationVO;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 飞控执行网关：直接持有 flight-hub-sdk 编译期类型，子类化 Wrapper 回调同步等待结果。
 * 与 {@link com.uavplatform.fh2.sdk.ReflectiveFh2SdkClient} 的反射风格不同——
 * Wrapper 系列回调是具体类（非接口），无法用 Proxy，只能编译期子类化。
 *
 * <p>硬约束（对齐 SDK 接入规范）：
 * <ul>
 *   <li>所有错误码非 0 显式抛出；</li>
 *   <li>控制权 acquire 之后必须提供 release 途径（本网关由会话层负责补偿释放）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DroneControlGateway {

    private static final long CONTROL_TIMEOUT_MS = 30_000L;
    private static final long DRC_TIMEOUT_MS = 20_000L;
    private static final long BATTERY_WAIT_MS = 5_000L;

    private final Fh2SdkClient fh2SdkClient;

    /** 同步等待结果 */
    public static class Await {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<FlightHubError> error = new AtomicReference<>();

        void ok() {
            latch.countDown();
        }

        void fail(FlightHubError e) {
            error.set(e);
            latch.countDown();
        }

        FlightHubError await(long timeoutMs) throws InterruptedException {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return null;
            }
            return error.get();
        }
    }

    private static boolean isError(FlightHubError error) {
        if (error == null) {
            return false;
        }
        try {
            // FlightHubSdkErrorCode.SUCCESS 的 swig 值为 0
            return error.getError_code() != null && error.getError_code().swigValue() != 0;
        } catch (Throwable t) {
            return true;
        }
    }

    private static String errorText(FlightHubError error) {
        if (error == null) {
            return "超时未收到设备应答";
        }
        try {
            return error.to_string();
        } catch (Throwable t) {
            return String.valueOf(error);
        }
    }

    public void requireSdk() {
        if (!fh2SdkClient.available()) {
            fh2SdkClient.initError().ifPresent(msg -> {
                throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE, "司空 SDK 不可用：" + msg);
            });
            throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE, "司空 SDK 不可用");
        }
    }

    public IDrone requireDrone(String droneSn) {
        requireSdk();
        if (!StringUtils.hasText(droneSn)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "未指定无人机 SN（请在对话面板选定设备）");
        }
        Object raw = fh2SdkClient.getRawDroneInstance(droneSn.trim());
        if (raw == null) {
            throw new BizException(ErrorCodes.CHAT_CONTROL_ERROR,
                    "获取无人机实例失败 sn=" + droneSn + "（设备可能离线）");
        }
        return (IDrone) raw;
    }

    // ---------- 控制权 / DRC ----------

    public void acquireControl(IDrone drone) {
        Await a = new Await();
        drone.getControlAuthModule().acquireFlightControlAuthorityWrapper(new ResultCallbackWithError() {
            @Override
            public void onResult(FlightHubError error) {
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "acquireFlightControlAuthority");
    }

    public void releaseControl(IDrone drone) {
        Await a = new Await();
        drone.getControlAuthModule().releaseFlightControlAuthorityWrapper(new ResultCallbackWithError() {
            @Override
            public void onResult(FlightHubError error) {
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "releaseFlightControlAuthority");
    }

    public void connectDrc(IDrone drone) {
        Await a = new Await();
        drone.getDrcModule().connectDrcLinkWrapper(new ResultCallback() {
            @Override
            public void onResult(FlightHubError error) {
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "connectDrcLink", DRC_TIMEOUT_MS);
    }

    public void disconnectDrc(IDrone drone) {
        Await a = new Await();
        drone.getDrcModule().disconnectDrcLinkWrapper(new ResultCallback() {
            @Override
            public void onResult(FlightHubError error) {
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "disconnectDrcLink", DRC_TIMEOUT_MS);
    }

    // ---------- 飞控指令 ----------

    /** 起飞到指定高度（lat/lon 为起飞点，通常取实时位置，避免飞向 0,0） */
    public Map<String, Object> takeoff(IDrone drone, double latitude, double longitude,
                                       double height, Integer rthAltitude, Integer maxSpeed) {
        TakeoffParams params = new TakeoffParams();
        params.setTarget_latitude(latitude);
        params.setTarget_longitude(longitude);
        params.setTarget_height(height);
        params.setRth_altitude(rthAltitude != null ? rthAltitude : 100);
        params.setSecurity_takeoff_height(height);
        params.setMax_speed(maxSpeed != null ? maxSpeed : 8);
        params.setOut_of_control_action(1);

        Await a = new Await();
        AtomicReference<TakeoffResult> resultRef = new AtomicReference<>();
        drone.getTakeoffModule().takeoffWrapper(params, new com.dji.flighthubsdk.generated.TakeoffResultCallback() {
            @Override
            public void onResult(FlightHubError error, TakeoffResult result) {
                resultRef.set(result);
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "takeoff");
        String flightId = resultRef.get() == null ? null : resultRef.get().getFlight_id();
        return Map.of("ok", true, "flightId", flightId == null ? "" : flightId,
                "height", height, "latitude", latitude, "longitude", longitude);
    }

    public Map<String, Object> returnHome(IDrone drone) {
        Await a = new Await();
        drone.getFlightControlModule().returnHomeWrapper(new ResultCallbackWithError() {
            @Override
            public void onResult(FlightHubError error) {
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "returnHome");
        return Map.of("ok", true, "message", "返航指令已下发");
    }

    public Map<String, Object> returnHomeCancel(IDrone drone) {
        Await a = new Await();
        drone.getFlightControlModule().returnHomeCancelWrapper(new ResultCallbackWithError() {
            @Override
            public void onResult(FlightHubError error) {
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "returnHomeCancel");
        return Map.of("ok", true, "message", "取消返航指令已下发");
    }

    public Map<String, Object> emergencyStop(IDrone drone) {
        Await a = new Await();
        drone.getFlightControlModule().drcEmergencyStopWrapper(new ResultCallbackWithError() {
            @Override
            public void onResult(FlightHubError error) {
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "emergencyStop");
        return Map.of("ok", true, "message", "紧急停止指令已下发，无人机将原地悬停");
    }

    /** 指点飞行（需先 acquire + connectDrc） */
    public Map<String, Object> flyTo(IDrone drone, double latitude, double longitude,
                                     double height, Integer maxSpeed) {
        FlyToPointParams params = new FlyToPointParams();
        params.setLatitude(latitude);
        params.setLongitude(longitude);
        params.setHeight(height);
        params.setMax_speed(maxSpeed != null ? maxSpeed : 10);

        Await a = new Await();
        drone.getFlyToPointModule().startWrapper(params,
                new com.dji.flighthubsdk.generated.FlyToPointStartCallback() {
                    @Override
                    public void onFlyToPointStart(FlightHubError error, String flyToId) {
                        if (isError(error)) {
                            a.fail(error);
                        } else {
                            a.ok();
                        }
                    }
                });
        finish(a, "flyTo.start");
        return Map.of("ok", true, "latitude", latitude, "longitude", longitude, "height", height);
    }

    public Map<String, Object> flyToStop(IDrone drone) {
        Await a = new Await();
        drone.getFlyToPointModule().drcStopWrapper(new ResultCallbackWithError() {
            @Override
            public void onResult(FlightHubError error) {
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "flyTo.stop");
        return Map.of("ok", true, "message", "指点飞行已停止，无人机原地悬停");
    }

    /** 航线任务暂停/恢复（FlightTaskControlModule） */
    public Map<String, Object> pauseMission(IDrone drone) {
        Await a = new Await();
        drone.getFlightTaskControlModule().pauseWrapper(new ResultCallbackWithError() {
            @Override
            public void onResult(FlightHubError error) {
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "flightTask.pause");
        return Map.of("ok", true, "message", "航线任务已暂停");
    }

    public Map<String, Object> resumeMission(IDrone drone) {
        Await a = new Await();
        drone.getFlightTaskControlModule().resumeWrapper(new ResultCallbackWithError() {
            @Override
            public void onResult(FlightHubError error) {
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "flightTask.resume");
        return Map.of("ok", true, "message", "航线任务已恢复");
    }

    /** 拍照；cameraKey 为镜头标识（wide/zoom/ir），默认广角 */
    public Map<String, Object> takePhoto(IDrone drone, String cameraKey) {
        Await a = new Await();
        String key = StringUtils.hasText(cameraKey) ? cameraKey.trim() : "wide";
        drone.getCameraControlModule().takePhotoWrapper(key, new ResultCallbackWithError() {
            @Override
            public void onResult(FlightHubError error) {
                if (isError(error)) {
                    a.fail(error);
                } else {
                    a.ok();
                }
            }
        });
        finish(a, "takePhoto");
        return Map.of("ok", true, "message", "拍照指令已下发（镜头 " + key + "）");
    }

    /** 读取电量（listen 一次后立即 cancel，遵守 listen/cancel 配对约束） */
    public Map<String, Object> battery(IDrone drone) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<com.dji.flighthubsdk.generated.AircraftBatteryInfo> ref = new AtomicReference<>();
        AtomicInteger listenId = new AtomicInteger(-1);
        try {
            listenId.set(drone.getDroneOsdModule().listenAircraftBatteryInfoWrapper(
                    new com.dji.flighthubsdk.generated.AircraftBatteryInfoCallback() {
                        @Override
                        public void onAircraftBatteryInfo(com.dji.flighthubsdk.generated.AircraftBatteryInfo info) {
                            if (info != null) {
                                ref.set(info);
                                latch.countDown();
                            }
                        }
                    }));
            latch.await(BATTERY_WAIT_MS, TimeUnit.MILLISECONDS);
            com.dji.flighthubsdk.generated.AircraftBatteryInfo info = ref.get();
            if (info == null) {
                throw new BizException(ErrorCodes.CHAT_CONTROL_ERROR, "未收到电量推送（无人机可能离线或在舱内）");
            }
            return Map.of(
                    "capacityPercent", info.getCapacity_percent(),
                    "remainFlightTime", info.getRemain_flight_time(),
                    "returnHomePower", info.getReturn_home_power());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCodes.CHAT_CONTROL_ERROR, "读取电量失败：" + e.getMessage());
        } finally {
            if (listenId.get() >= 0) {
                try {
                    drone.getDroneOsdModule().cancelListenAircraftBatteryInfoWrapper(listenId.get());
                } catch (Throwable t) {
                    log.warn("[chat-control] cancelListenAircraftBatteryInfo failed: {}", t.getMessage());
                }
            }
        }
    }

    // ---------- 机场远程调试（开关机 / 舱盖，直播大屏对关机飞行器先开机再拉流） ----------

    /** 同步等待步的超时：前端 HTTP 超时 15s，这里必须留出余量 */
    private static final long DOCK_DEBUG_SYNC_TIMEOUT_MS = 12_000L;
    /** 后台步（舱盖/上电等机械动作）超时放宽 */
    private static final long DOCK_DEBUG_BG_TIMEOUT_MS = 120_000L;

    private static final AtomicInteger DOCK_DEBUG_SEQ = new AtomicInteger();

    /** 机械动作命令的执行线程（命令回调在 SDK 线程，此处仅编排顺序） */
    private static final ExecutorService DOCK_DEBUG_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "dock-debug-" + DOCK_DEBUG_SEQ.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    /** bean 层错误等待：core.DeviceDebugModule 回调错误是 bean.FlightHubError（isSuccess 判错），与 generated Await 不通用 */
    public static class BeanAwait {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile String error;

        void ok() {
            latch.countDown();
        }

        void fail(String msg) {
            error = msg;
            latch.countDown();
        }

        void finish(String action, long timeoutMs) {
            boolean done;
            try {
                done = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException(ErrorCodes.CHAT_CONTROL_ERROR, action + " 被中断");
            }
            if (!done) {
                throw new BizException(ErrorCodes.CHAT_CONTROL_ERROR, action + " 超时未收到设备应答");
            }
            if (error != null) {
                log.warn("[chat-control] {} failed: {}", action, error);
                throw new BizException(ErrorCodes.CHAT_CONTROL_ERROR, action + " 失败：" + error);
            }
        }
    }

    private static boolean beanOk(com.dji.flighthubsdk.bean.FlightHubError error) {
        return error == null || error.isSuccess();
    }

    public Dock requireDock(String dockSn) {
        requireSdk();
        if (!StringUtils.hasText(dockSn)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "未指定机场 SN");
        }
        Dock dock = FlightHubSDK.getDockInstance(dockSn.trim());
        if (dock == null) {
            throw new BizException(ErrorCodes.CHAT_CONTROL_ERROR,
                    "获取机场实例失败 sn=" + dockSn + "（设备可能离线）");
        }
        return dock;
    }

    /**
     * 机场开机上电：进入调试模式（同步等待，机场离线/占用立即失败）→
     * 开舱盖 + 飞行器上电（后台执行，机械动作约 1~2 分钟）。
     */
    public Map<String, Object> dockPowerOn(String dockSn) {
        String sn = dockSn.trim();
        DeviceDebugModule debug = requireDock(sn).getDeviceDebugModule();

        BeanAwait debugMode = new BeanAwait();
        debug.openDebugMode(sn, (error, result) -> {
            if (beanOk(error)) {
                debugMode.ok();
            } else {
                debugMode.fail(error.toString());
            }
        });
        debugMode.finish("openDebugMode", DOCK_DEBUG_SYNC_TIMEOUT_MS);

        DOCK_DEBUG_POOL.execute(() -> {
            try {
                BeanAwait cover = new BeanAwait();
                debug.openDockCover(sn, (error, result) -> {
                    if (beanOk(error)) {
                        cover.ok();
                    } else {
                        cover.fail(error.toString());
                    }
                });
                cover.finish("openDockCover", DOCK_DEBUG_BG_TIMEOUT_MS);

                BeanAwait drone = new BeanAwait();
                debug.openDrone(sn, (error, result) -> {
                    if (beanOk(error)) {
                        drone.ok();
                    } else {
                        drone.fail(error.toString());
                    }
                });
                drone.finish("openDrone", DOCK_DEBUG_BG_TIMEOUT_MS);
                log.info("[chat-control] dock power-on completed sn={}", sn);
            } catch (BizException e) {
                log.warn("[chat-control] dock power-on background step failed sn={}: {}", sn, e.getMessage());
            }
        });
        return Map.of("accepted", true,
                "message", "已进入调试模式，开舱盖与飞行器上电正在后台执行（约 1~2 分钟）");
    }

    /**
     * 机场关机断电：飞行器断电（同步等待，在飞/充电中会被设备拒绝）→
     * 关舱盖 + 退出调试模式（后台执行）。
     */
    public Map<String, Object> dockPowerOff(String dockSn) {
        String sn = dockSn.trim();
        DeviceDebugModule debug = requireDock(sn).getDeviceDebugModule();

        BeanAwait drone = new BeanAwait();
        debug.closeDrone(sn, (error, result) -> {
            if (beanOk(error)) {
                drone.ok();
            } else {
                drone.fail(error.toString());
            }
        });
        drone.finish("closeDrone", DOCK_DEBUG_SYNC_TIMEOUT_MS);

        DOCK_DEBUG_POOL.execute(() -> {
            try {
                BeanAwait cover = new BeanAwait();
                debug.closeDockCover(sn, (error, result) -> {
                    if (beanOk(error)) {
                        cover.ok();
                    } else {
                        cover.fail(error.toString());
                    }
                });
                cover.finish("closeDockCover", DOCK_DEBUG_BG_TIMEOUT_MS);

                BeanAwait debugMode = new BeanAwait();
                debug.closeDebugMode(sn, (error, result) -> {
                    if (beanOk(error)) {
                        debugMode.ok();
                    } else {
                        debugMode.fail(error.toString());
                    }
                });
                debugMode.finish("closeDebugMode", DOCK_DEBUG_SYNC_TIMEOUT_MS);
                log.info("[chat-control] dock power-off completed sn={}", sn);
            } catch (BizException e) {
                log.warn("[chat-control] dock power-off background step failed sn={}: {}", sn, e.getMessage());
            }
        });
        return Map.of("accepted", true, "message", "飞行器断电指令已执行，关舱盖与退出调试模式正在后台完成");
    }

    /** 实时位置（复用 SDK 门面的 listen+cancel 拉取） */
    public Fh2AircraftLocationVO location(String droneSn) {
        requireSdk();
        for (Fh2AircraftLocationVO vo : fh2SdkClient.listFlyingAircraftLocations()) {
            if (droneSn != null && droneSn.equals(vo.getSn())) {
                return vo;
            }
        }
        return null;
    }

    private void finish(Await a, String action) {
        finish(a, action, CONTROL_TIMEOUT_MS);
    }

    private void finish(Await a, String action, long timeoutMs) {
        try {
            FlightHubError error = a.await(timeoutMs);
            if (isError(error)) {
                log.warn("[chat-control] {} failed: {}", action, errorText(error));
                throw new BizException(ErrorCodes.CHAT_CONTROL_ERROR,
                        action + " 失败：" + errorText(error));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCodes.CHAT_CONTROL_ERROR, action + " 被中断");
        }
    }
}
