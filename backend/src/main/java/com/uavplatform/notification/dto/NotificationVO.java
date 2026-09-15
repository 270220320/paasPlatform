package com.uavplatform.notification.dto;

import lombok.Builder;
import lombok.Data;

/** 站内通知 VO（列表与 SSE 推送共用结构）。 */
@Data
@Builder
public class NotificationVO {

    private Long id;
    private String type;
    private String level;
    private String title;
    private String content;
    private String refType;
    private Long refId;
    private Boolean read;
    private String createdAt;
}
