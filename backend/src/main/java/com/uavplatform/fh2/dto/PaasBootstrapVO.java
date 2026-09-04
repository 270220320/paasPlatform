package com.uavplatform.fh2.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 前端 paas.js / WaylineEditor / DeviceManager 启动参数。
 */
@Data
@Builder
public class PaasBootstrapVO {

    private boolean enabled;
    private String frontHost;
    private String serverHost;
    private String gatewayBase;
    private String wssUrl;
    private String userToken;
    private String bindingUuid;
    /** 组织 UUID（常从 token 解析，OSS 前缀用） */
    private String organizationUuid;
    private String projectUuid;
    /** WaylineEditor oss.objectKeyPrefix */
    private String objectKeyPrefix;
    private String waylineSdkCss;
    private String waylineSdkJs;
    private String deviceSdkCss;
    private String deviceSdkJs;
    private String replaySdkCss;
    private String replaySdkJs;
    /** 直播大屏（DJILivePlayer）SDK 资源 */
    private String liveSdkCss;
    private String liveSdkJs;
    private String authHeaderName;
}
