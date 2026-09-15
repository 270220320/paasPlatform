package com.uavplatform.wayline.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaylineDetailVO {

    private Long id;
    private String name;
    private String aircraftModel;
    private String waylineType;
    private String status;
    private String source;
    private String fh2WaylineUuid;
    private Long folderId;
    private String updatedAt;
    /** 导入接口专用：true 表示恢复更新了既有同 uuid 记录（不新增列表行） */
    private Boolean restored;
    private WaylineContentVO content;
}
