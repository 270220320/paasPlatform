package com.uavplatform.media.dto;

import lombok.Data;

import java.util.List;

@Data
public class TrashItemVO {

    private String itemType;
    private Long id;
    private String name;
    private String createdAt;

    public static TrashItemVO media(Long id, String name, String createdAt) {
        TrashItemVO vo = new TrashItemVO();
        vo.setItemType("media");
        vo.setId(id);
        vo.setName(name);
        vo.setCreatedAt(createdAt);
        return vo;
    }

    public static TrashItemVO model(Long id, String name, String createdAt) {
        TrashItemVO vo = new TrashItemVO();
        vo.setItemType("model");
        vo.setId(id);
        vo.setName(name);
        vo.setCreatedAt(createdAt);
        return vo;
    }
}
