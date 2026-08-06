package com.uavplatform.mission.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 飞行任务调度：到期下发兜底 + 司空状态回写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionScheduler {

    private final MissionDispatchService missionDispatchService;

    /** 每 30 秒：到期未下发的立即/单次任务 */
    @Scheduled(fixedDelayString = "${uav.mission.dispatch-interval-ms:30000}")
    public void dispatchDue() {
        try {
            missionDispatchService.dispatchDueExecutions();
        } catch (Exception e) {
            log.warn("[mission-scheduler] dispatchDue failed: {}", e.getMessage());
        }
    }

    /** 每 20 秒：同步司空执行状态 */
    @Scheduled(fixedDelayString = "${uav.mission.sync-interval-ms:20000}")
    public void syncStatuses() {
        try {
            missionDispatchService.syncOpenRemoteStatuses();
        } catch (Exception e) {
            log.warn("[mission-scheduler] syncStatuses failed: {}", e.getMessage());
        }
    }
}
