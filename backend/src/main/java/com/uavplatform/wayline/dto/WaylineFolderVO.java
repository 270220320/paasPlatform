package com.uavplatform.wayline.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaylineFolderVO {

    private Long id;
    private String name;
    private Long parentId;
    private String createdAt;
    /** 其下航线数（前端据此隐藏删除按钮，有航线不可删） */
    private Long waylineCount;
}
