package com.uavplatform.device.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.device.domain.DeviceEntity;
import com.uavplatform.device.domain.DeviceIssueReportEntity;
import com.uavplatform.device.dto.DeviceIssueCreateRequest;
import com.uavplatform.device.dto.DeviceIssueVO;
import com.uavplatform.device.mapper.DeviceIssueReportMapper;
import com.uavplatform.device.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceIssueService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TRACK_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DeviceIssueReportMapper deviceIssueReportMapper;
    private final DeviceMapper deviceMapper;

    @Transactional(rollbackFor = Exception.class)
    public DeviceIssueVO create(DeviceIssueCreateRequest request) {
        PermissionChecker.require("device:write");
        DeviceEntity device = deviceMapper.selectById(request.getDeviceId());
        if (device == null) {
            throw new BizException(ErrorCodes.DEVICE_NOT_FOUND, "设备不存在");
        }
        DeviceIssueReportEntity entity = new DeviceIssueReportEntity();
        entity.setDeviceId(request.getDeviceId());
        entity.setDescription(request.getDescription().trim());
        entity.setTrackingNo(generateTrackingNo());
        entity.setStatus("submitted");
        entity.setCreatedBy(currentUserId());
        deviceIssueReportMapper.insert(entity);
        return toVO(entity);
    }

    public List<DeviceIssueVO> list(Long deviceId) {
        PermissionChecker.require("device:read");
        LambdaQueryWrapper<DeviceIssueReportEntity> qw = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            qw.eq(DeviceIssueReportEntity::getDeviceId, deviceId);
        }
        qw.orderByDesc(DeviceIssueReportEntity::getCreatedAt);
        return deviceIssueReportMapper.selectList(qw).stream().map(this::toVO).collect(Collectors.toList());
    }

    private String generateTrackingNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "ISSUE-" + LocalDateTime.now().format(TRACK_FMT) + "-" + suffix;
    }

    private DeviceIssueVO toVO(DeviceIssueReportEntity e) {
        return DeviceIssueVO.builder()
                .id(e.getId())
                .deviceId(e.getDeviceId())
                .description(e.getDescription())
                .trackingNo(e.getTrackingNo())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt() == null ? null : e.getCreatedAt().format(FMT))
                .build();
    }

    private Long currentUserId() {
        LoginUser u = UserContext.get();
        return u == null ? null : u.getUserId();
    }
}
