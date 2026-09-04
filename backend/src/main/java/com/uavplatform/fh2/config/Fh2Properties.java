package com.uavplatform.fh2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 司空 PaaS / FlightHub Java SDK 配置（可由 application.yml 或 t_fh2_config 覆盖）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "uav.fh2")
public class Fh2Properties {

    /** 总开关 */
    private boolean enabled = true;

    /** paas.js 所在站点 */
    private String frontHost = "http://192.168.76.141";

    /** 后端服务地址（不带 /gateway） */
    private String serverHost = "http://192.168.76.141:30812";

    /** WaylineEditor / FlyDataReplay 用网关 base（带 /gateway） */
    private String gatewayBase = "http://192.168.76.141:30812/gateway";

    /** 推送中心 WebSocket */
    private String wssUrl = "ws://192.168.76.141:30812/openapi/v0.9/notification/ws/v1/notifications";

    /** 前端 paas.js / SDK 鉴权头 X-User-Token；同时作为 Java SDK token */
    private String userToken = "";

    /** 工作空间 binding_uuid */
    private String bindingUuid = "";

    /**
     * OpenAPI 项目 UUID（X-Project-Uuid）。
     * Token 内 project_uuid 常为空，私有化请在此或 t_fh2_config 配置。
     */
    private String projectUuid = "";

    /** 三维重建默认质量：high / medium / low（对齐 demo setQualityLevel(1)=high） */
    private String reconstructionQualityLevel = "high";

    /** 三维重建坐标系 WKT / EPSG（EPSG:32650 = UTM Zone 50N, 覆盖 114°E~120°E） */
    private String reconstructionWkt = "EPSG:32650";

    /** 三维重建场景：normal / surround */
    private String reconstructionMode = "normal";

    /** 模型简化系数（对齐 demo 0.8） */
    private Double reconstructionSimplifiedFactor = 0.8;

    /** 三维重建 SfM 方法（demo 默认 GLOBAL） */
    private String reconstructionSfmMethod = "GLOBAL";

    /** 三维 outputGeoDesc 高程覆盖（demo 示例 EPSG:5737，空则不下发） */
    private String reconstructionVerticalCs = "EPSG:5737";

    /**
     * 手动指定媒体文件夹 UUID（与 demo ConfigManager.terraBuildFolderUuid 对齐）。
     * 自动获取不到时使用此值；空则仅依赖 folderIds。
     */
    private String terraBuildFolderUuid = "";

    /** 原生 SDK 日志文件（相对后端工作目录） */
    private String sdkLogFilePath = "logs/fh2-sdk.log";

    private long sdkLogMaxFileSize = 4L * 1024 * 1024;

    private int sdkLogMaxFiles = 100;

    /** Java SDK address（scheme+host，不含端口） */
    private String sdkAddress = "http://192.168.76.141";

    /** Java SDK port */
    private String sdkPort = "30812";

    /** Java SDK 通知服务完整 WS 地址，默认等于 wssUrl */
    private String notificationServerAddress = "";

    private String waylineSdkCss = "http://192.168.76.141/paasjs-demo/wayline-edit-sdk/index.css";
    private String waylineSdkJs = "http://192.168.76.141/paasjs-demo/wayline-edit-sdk/index.js";
    private String deviceSdkCss = "http://192.168.76.141/paasjs-demo/device-manager-sdk-v2/index.css";
    private String deviceSdkJs = "http://192.168.76.141/paasjs-demo/device-manager-sdk-v2/index.js";
    private String replaySdkCss = "http://192.168.76.141/paasjs-demo/fly-data-sdk/index.css";
    private String replaySdkJs = "http://192.168.76.141/paasjs-demo/fly-data-sdk/index.js";
    /** 直播大屏（DJILivePlayer）SDK 资源，完全独立、不依赖 paas.js */
    private String liveSdkCss = "http://192.168.76.141/paasjs-demo/multi-stream-cdn/sdk/style.css";
    private String liveSdkJs = "http://192.168.76.141/paasjs-demo/multi-stream-cdn/sdk/sdk.umd.js";

    /** WaylineEditor OSS 上传 key 前缀；空则用 token 内 organization_uuid */
    private String objectKeyPrefix = "";

    /** dji2 后台（/safety /route /task），对齐 paasVue routeApiBaseUrl */
    private String routeApiBase = "http://192.168.76.141:56789";

    /** 网关额外鉴权头（私有化 demo 常用） */
    private String extraHttpHeaderKey = "";
    private String extraHttpHeaderValue = "";

    /** 是否尝试加载 FlightHub 原生 SDK（需 jar + Linux/mac JNI） */
    private boolean sdkNativeEnabled = true;

    public String resolvedNotificationServer() {
        if (notificationServerAddress != null && !notificationServerAddress.isBlank()) {
            return notificationServerAddress;
        }
        return wssUrl;
    }
}
