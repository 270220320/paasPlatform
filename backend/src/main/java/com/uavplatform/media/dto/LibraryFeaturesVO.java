package com.uavplatform.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LibraryFeaturesVO {

    /** 媒体库列表/下载是否走司空 FlightHub 原生 SDK（目标服务见 uav.fh2.sdk-address） */
    private boolean fh2SdkAvailable;
    /** SDK 初始化失败原因，供前端提示 */
    private String fh2SdkError;
    private boolean smartSearch;
    private boolean changeDetectionPro;
    private boolean ultraPanorama;
}
