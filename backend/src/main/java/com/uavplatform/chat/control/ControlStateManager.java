package com.uavplatform.chat.control;

import com.dji.flighthubsdk.generated.IDrone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 会话级飞行控制权状态：acquire 后保持到会话释放（幂等），DRC 按工具执行期临时建立。
 * 遵循「acquire 后必须可 release」约束：会话关闭时调用 {@link #releaseIfHeld}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ControlStateManager {

    private final DroneControlGateway gateway;

    /** sessionId → 持有控制权的 droneSn */
    private final Map<Long, String> heldBySession = new ConcurrentHashMap<>();

    /** 确保会话持有指定无人机的飞行控制权（幂等） */
    public void ensureControl(long sessionId, String droneSn) {
        String held = heldBySession.get(sessionId);
        if (droneSn != null && droneSn.equals(held)) {
            return;
        }
        if (held != null) {
            // 切换目标机：先释放旧的
            releaseIfHeld(sessionId);
        }
        IDrone drone = gateway.requireDrone(droneSn);
        gateway.acquireControl(drone);
        heldBySession.put(sessionId, droneSn);
        log.info("[chat-control] session {} acquired control of {}", sessionId, droneSn);
    }

    /** 在 DRC 链路内执行（连接 → 执行 → 断开，异常也断开） */
    public <T> T withDrc(long sessionId, String droneSn, Supplier<T> action) {
        ensureControl(sessionId, droneSn);
        IDrone drone = gateway.requireDrone(droneSn);
        gateway.connectDrc(drone);
        try {
            return action.get();
        } finally {
            try {
                gateway.disconnectDrc(drone);
            } catch (Exception e) {
                log.warn("[chat-control] session {} disconnectDrc failed: {}", sessionId, e.getMessage());
            }
        }
    }

    public boolean isHeld(long sessionId) {
        return heldBySession.containsKey(sessionId);
    }

    /** 释放会话持有的控制权（未持有时静默） */
    public void releaseIfHeld(long sessionId) {
        String sn = heldBySession.remove(sessionId);
        if (sn == null) {
            return;
        }
        try {
            IDrone drone = gateway.requireDrone(sn);
            gateway.releaseControl(drone);
            log.info("[chat-control] session {} released control of {}", sessionId, sn);
        } catch (Exception e) {
            log.warn("[chat-control] session {} release control of {} failed: {}", sessionId, sn, e.getMessage());
        }
    }
}
