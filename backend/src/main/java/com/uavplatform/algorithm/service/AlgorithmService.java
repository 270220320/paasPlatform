package com.uavplatform.algorithm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.uavplatform.algorithm.domain.AlgorithmAlarmEntity;
import com.uavplatform.algorithm.domain.AlgorithmEntity;
import com.uavplatform.algorithm.dto.AlgorithmAlarmCreateRequest;
import com.uavplatform.algorithm.dto.AlgorithmAlarmVO;
import com.uavplatform.algorithm.dto.AlgorithmCapabilitiesVO;
import com.uavplatform.algorithm.dto.AlgorithmUpdateRequest;
import com.uavplatform.algorithm.dto.AlgorithmVO;
import com.uavplatform.algorithm.mapper.AlgorithmAlarmMapper;
import com.uavplatform.algorithm.mapper.AlgorithmMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlgorithmService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlgorithmMapper algorithmMapper;
    private final AlgorithmAlarmMapper algorithmAlarmMapper;

    public List<AlgorithmVO> list() {
        PermissionChecker.require("algorithm:read");
        return algorithmMapper.selectList(new LambdaQueryWrapper<AlgorithmEntity>()
                        .eq(AlgorithmEntity::getWorkspaceId, workspaceId())
                        .orderByDesc(AlgorithmEntity::getUpdatedAt))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public AlgorithmCapabilitiesVO capabilities() {
        PermissionChecker.require("algorithm:read");
        Long multimodal = algorithmMapper.selectCount(new LambdaQueryWrapper<AlgorithmEntity>()
                .eq(AlgorithmEntity::getWorkspaceId, workspaceId())
                .eq(AlgorithmEntity::getStatus, "enabled")
                .eq(AlgorithmEntity::getAlgoType, "multimodal"));
        Long detection = algorithmMapper.selectCount(new LambdaQueryWrapper<AlgorithmEntity>()
                .eq(AlgorithmEntity::getWorkspaceId, workspaceId())
                .eq(AlgorithmEntity::getStatus, "enabled")
                .eq(AlgorithmEntity::getAlgoType, "detection"));
        return AlgorithmCapabilitiesVO.builder()
                .multimodalAvailable(multimodal != null && multimodal > 0)
                .enabledDetectionCount(detection == null ? 0 : detection.intValue())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public AlgorithmVO update(Long id, AlgorithmUpdateRequest request) {
        PermissionChecker.require("algorithm:write");
        AlgorithmEntity entity = requireAlgorithm(id);
        if ("not_opened".equals(entity.getStatus())) {
            throw new BizException(ErrorCodes.ALGORITHM_NOT_OPENED, "算法未开通");
        }
        if ("enabled".equals(request.getStatus()) && "not_opened".equals(entity.getStatus())) {
            throw new BizException(ErrorCodes.ALGORITHM_NOT_OPENED, "算法未开通");
        }
        entity.setStatus(request.getStatus());
        if (request.getConfig() != null) {
            entity.setConfig(request.getConfig());
        }
        algorithmMapper.updateById(entity);
        return toVO(entity);
    }

    public PageResult<AlgorithmAlarmVO> listAlarms(Long algorithmId, String start, String end,
                                                   long page, long pageSize) {
        PermissionChecker.require("algorithm:read");
        long p = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        LambdaQueryWrapper<AlgorithmAlarmEntity> qw = new LambdaQueryWrapper<>();
        if (algorithmId != null) {
            qw.eq(AlgorithmAlarmEntity::getAlgorithmId, algorithmId);
        }
        if (StringUtils.hasText(start)) {
            qw.ge(AlgorithmAlarmEntity::getRaisedAt, LocalDateTime.parse(start, FMT));
        }
        if (StringUtils.hasText(end)) {
            qw.le(AlgorithmAlarmEntity::getRaisedAt, LocalDateTime.parse(end, FMT));
        }
        qw.orderByDesc(AlgorithmAlarmEntity::getRaisedAt);
        Page<AlgorithmAlarmEntity> result = algorithmAlarmMapper.selectPage(new Page<>(p, size), qw);
        List<AlgorithmAlarmVO> list = result.getRecords().stream().map(this::toAlarmVO).collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), p, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public IdVO createAlarm(AlgorithmAlarmCreateRequest request) {
        PermissionChecker.require("algorithm:write");
        requireAlgorithm(request.getAlgorithmId());
        AlgorithmAlarmEntity alarm = new AlgorithmAlarmEntity();
        alarm.setAlgorithmId(request.getAlgorithmId());
        alarm.setTargetLabel(request.getTargetLabel());
        alarm.setSource(request.getSource());
        alarm.setPayload(request.getPayload());
        alarm.setRaisedAt(LocalDateTime.now());
        algorithmAlarmMapper.insert(alarm);
        return new IdVO(alarm.getId());
    }

    private AlgorithmEntity requireAlgorithm(Long id) {
        AlgorithmEntity entity = algorithmMapper.selectById(id);
        if (entity == null || !workspaceId().equals(entity.getWorkspaceId())) {
            throw new BizException(ErrorCodes.ALGORITHM_NOT_FOUND, "算法不存在");
        }
        return entity;
    }

    private AlgorithmVO toVO(AlgorithmEntity e) {
        return AlgorithmVO.builder()
                .id(e.getId())
                .name(e.getName())
                .algoType(e.getAlgoType())
                .status(e.getStatus())
                .updatedAt(e.getUpdatedAt() == null ? null : e.getUpdatedAt().format(FMT))
                .build();
    }

    private AlgorithmAlarmVO toAlarmVO(AlgorithmAlarmEntity a) {
        AlgorithmEntity algo = algorithmMapper.selectById(a.getAlgorithmId());
        return AlgorithmAlarmVO.builder()
                .id(a.getId())
                .algorithmId(a.getAlgorithmId())
                .algorithmName(algo == null ? null : algo.getName())
                .targetLabel(a.getTargetLabel())
                .source(a.getSource())
                .raisedAt(a.getRaisedAt() == null ? null : a.getRaisedAt().format(FMT))
                .payload(a.getPayload())
                .build();
    }

    private Long workspaceId() {
        LoginUser u = UserContext.get();
        return u == null || u.getWorkspaceId() == null ? 1L : u.getWorkspaceId();
    }
}
