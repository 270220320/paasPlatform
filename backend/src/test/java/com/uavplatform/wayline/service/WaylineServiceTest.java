package com.uavplatform.wayline.service;

import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import com.uavplatform.mission.mapper.MissionPlanMapper;
import com.uavplatform.wayline.dto.WaylineCreateRequest;
import com.uavplatform.wayline.mapper.WaylineContentMapper;
import com.uavplatform.wayline.mapper.WaylineFolderMapper;
import com.uavplatform.wayline.mapper.WaylineMapper;
import com.uavplatform.wayline.mapper.WaylineSnapshotMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class WaylineServiceTest {

    @Mock
    private WaylineMapper waylineMapper;
    @Mock
    private WaylineContentMapper waylineContentMapper;
    @Mock
    private WaylineFolderMapper waylineFolderMapper;
    @Mock
    private WaylineSnapshotMapper waylineSnapshotMapper;
    @Mock
    private MissionPlanMapper missionPlanMapper;
    @Mock
    private Fh2SdkClient fh2SdkClient;

    private WaylineService waylineService;

    @BeforeEach
    void setUp() {
        waylineService = new WaylineService(
                waylineMapper, waylineContentMapper, waylineFolderMapper,
                waylineSnapshotMapper, missionPlanMapper, fh2SdkClient);
        LoginUser user = new LoginUser();
        user.setPermissions(List.of("wayline:write"));
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void create_unsupportedType_shouldFail() {
        WaylineCreateRequest req = new WaylineCreateRequest();
        req.setName("test");
        req.setAircraftModel("M350 RTK");
        req.setWaylineType("slope");

        BizException ex = assertThrows(BizException.class, () -> waylineService.create(req));
        assertEquals(ErrorCodes.WAYLINE_TYPE_UNSUPPORTED, ex.getCode());
    }
}
