package com.uavplatform.mission.service;

import com.uavplatform.algorithm.mapper.AlgorithmMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.device.mapper.DeviceMapper;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import com.uavplatform.media.mapper.MediaAssetMapper;
import com.uavplatform.mission.domain.MissionExecutionEntity;
import com.uavplatform.mission.domain.MissionPlanEntity;
import com.uavplatform.mission.dto.CreateMissionCmd;
import com.uavplatform.mission.mapper.MissionExecutionMapper;
import com.uavplatform.mission.mapper.MissionPlanMapper;
import com.uavplatform.wayline.mapper.WaylineContentMapper;
import com.uavplatform.wayline.mapper.WaylineMapper;
import com.uavplatform.wayline.service.WaylineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock
    private MissionPlanMapper missionPlanMapper;
    @Mock
    private MissionExecutionMapper missionExecutionMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private WaylineService waylineService;
    @Mock
    private WaylineMapper waylineMapper;
    @Mock
    private WaylineContentMapper waylineContentMapper;
    @Mock
    private AlgorithmMapper algorithmMapper;
    @Mock
    private MediaAssetMapper mediaAssetMapper;
    @Mock
    private Fh2SdkClient fh2SdkClient;
    @Mock
    private MissionDispatchService missionDispatchService;
    @Mock
    private MissionReconstructService missionReconstructService;

    private MissionService missionService;

    @BeforeEach
    void setUp() {
        missionService = new MissionService(
                missionPlanMapper, missionExecutionMapper, deviceMapper,
                waylineService, waylineMapper, waylineContentMapper, algorithmMapper,
                mediaAssetMapper, fh2SdkClient,
                missionDispatchService, missionReconstructService);
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setWorkspaceId(1L);
        user.setPermissions(List.of("mission:write"));
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void create_missingWaylineForNormalTask_shouldFail() {
        CreateMissionCmd cmd = baseCmd();
        cmd.setTaskType("normal");
        cmd.setWaylineId(null);

        BizException ex = assertThrows(BizException.class, () -> missionService.create(cmd));
        assertEquals(ErrorCodes.MISSION_PARAM_MISSING, ex.getCode());
    }

    @Test
    void create_invalidMinHeight_shouldFail() {
        CreateMissionCmd cmd = baseCmd();
        cmd.setTaskType("manual");
        cmd.setMinFlightHeightM(BigDecimal.valueOf(10));

        BizException ex = assertThrows(BizException.class, () -> missionService.create(cmd));
        assertEquals(ErrorCodes.BAD_REQUEST, ex.getCode());
    }

    @Test
    void delete_running_shouldFail() {
        MissionPlanEntity plan = planWithStatus("running");
        when(missionPlanMapper.selectById(7L)).thenReturn(plan);

        BizException ex = assertThrows(BizException.class, () -> missionService.delete(7L));
        assertEquals(ErrorCodes.MISSION_DELETE_FORBIDDEN, ex.getCode());
        verify(missionPlanMapper, never()).deleteById(anyLong());
        verify(missionExecutionMapper, never()).delete(any());
    }

    @Test
    void delete_completed_shouldSoftDeletePlanAndExecutions() {
        MissionPlanEntity plan = planWithStatus("completed");
        when(missionPlanMapper.selectById(7L)).thenReturn(plan);

        missionService.delete(7L);

        verify(missionDispatchService, times(1)).cancelRemoteTasks(7L);
        verify(missionExecutionMapper, times(1)).delete(any());
        verify(missionPlanMapper, times(1)).deleteById(7L);
    }

    @Test
    void delete_cancelled_shouldSucceedEvenIfCancelRemoteFails() {
        MissionPlanEntity plan = planWithStatus("cancelled");
        when(missionPlanMapper.selectById(8L)).thenReturn(plan);
        doThrow(new RuntimeException("fh2 down")).when(missionDispatchService).cancelRemoteTasks(8L);

        // 远端取消失败不应阻断本地删除
        missionService.delete(8L);

        verify(missionExecutionMapper, times(1)).delete(any());
        verify(missionPlanMapper, times(1)).deleteById(8L);
    }

    @Test
    void delete_notFound_shouldFail() {
        when(missionPlanMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> missionService.delete(99L));
        assertEquals(ErrorCodes.MISSION_NOT_FOUND, ex.getCode());
        verify(missionPlanMapper, never()).deleteById(anyLong());
    }

    private MissionPlanEntity planWithStatus(String status) {
        MissionPlanEntity plan = new MissionPlanEntity();
        plan.setId(1L);
        plan.setWorkspaceId(1L);
        plan.setName("plan");
        plan.setStatus(status);
        return plan;
    }

    private CreateMissionCmd baseCmd() {
        CreateMissionCmd cmd = new CreateMissionCmd();
        cmd.setName("test mission");
        cmd.setTaskType("manual");
        cmd.setDeviceId(1L);
        cmd.setStrategy("immediate");
        cmd.setAccuracyMode("rtk");
        cmd.setMinFlightHeightM(BigDecimal.valueOf(60));
        cmd.setSignalLostAction("rth");
        return cmd;
    }
}
