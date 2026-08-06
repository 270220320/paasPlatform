package com.uavplatform.device.service;

import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.device.domain.BindCodeEntity;
import com.uavplatform.device.domain.WorkspaceEntity;
import com.uavplatform.device.dto.BindCodeVO;
import com.uavplatform.device.mapper.BindCodeMapper;
import com.uavplatform.device.mapper.DeviceMapper;
import com.uavplatform.device.mapper.DeviceStatusMapper;
import com.uavplatform.device.mapper.WorkspaceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private DeviceStatusMapper deviceStatusMapper;
    @Mock
    private BindCodeMapper bindCodeMapper;
    @Mock
    private WorkspaceMapper workspaceMapper;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(deviceMapper, deviceStatusMapper, bindCodeMapper, workspaceMapper);
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setWorkspaceId(1L);
        user.setPermissions(List.of("device:read", "device:write"));
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void currentBindCode_returnsActive() {
        BindCodeEntity entity = new BindCodeEntity();
        entity.setId(1L);
        entity.setWorkspaceId(1L);
        entity.setCode("BIND-TEST-0001");
        entity.setStatus("active");
        entity.setExpireAt(LocalDateTime.now().plusDays(3));
        when(bindCodeMapper.selectOne(any())).thenReturn(entity);
        WorkspaceEntity ws = new WorkspaceEntity();
        ws.setId(1L);
        ws.setWorkspaceCode("aiflow-workspace-001");
        when(workspaceMapper.selectById(1L)).thenReturn(ws);

        BindCodeVO vo = deviceService.currentBindCode();
        assertNotNull(vo);
        assertEquals("BIND-TEST-0001", vo.getCode());
        assertEquals("aiflow-workspace-001", vo.getWorkspaceCode());
    }

    @Test
    void page_withoutPermission_shouldFail() {
        UserContext.clear();
        LoginUser user = new LoginUser();
        user.setPermissions(List.of());
        UserContext.set(user);
        BizException ex = assertThrowsBiz(() -> deviceService.page("dock", null, null, 1, 50));
        assertEquals(ErrorCodes.FORBIDDEN, ex.getCode());
    }

    private BizException assertThrowsBiz(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected BizException");
        } catch (BizException ex) {
            return ex;
        }
    }
}
