package com.uavplatform.map.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.device.mapper.DeviceMapper;
import com.uavplatform.map.mapper.AnnotationFolderMapper;
import com.uavplatform.map.mapper.AnnotationMapper;
import com.uavplatform.map.mapper.SyncJobItemMapper;
import com.uavplatform.map.mapper.SyncJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AnnotationServiceTest {

    @Mock
    private AnnotationMapper annotationMapper;
    @Mock
    private AnnotationFolderMapper annotationFolderMapper;
    @Mock
    private SyncJobMapper syncJobMapper;
    @Mock
    private SyncJobItemMapper syncJobItemMapper;
    @Mock
    private DeviceMapper deviceMapper;

    private AnnotationService annotationService;

    @BeforeEach
    void setUp() {
        annotationService = new AnnotationService(
                annotationMapper, annotationFolderMapper, syncJobMapper, syncJobItemMapper,
                deviceMapper, new ObjectMapper());
    }

    @Test
    void validateGeometry_mismatchType_shouldFail() {
        BizException ex = assertThrows(BizException.class, () ->
                annotationService.validateGeometry("point", "{\"type\":\"Polygon\",\"coordinates\":[]}"));
        assertEquals(ErrorCodes.GEOMETRY_INVALID, ex.getCode());
    }

    @Test
    void validateGeometry_validPoint_shouldPass() {
        annotationService.validateGeometry("point",
                "{\"type\":\"Point\",\"coordinates\":[116.3,39.9]}");
    }
}
