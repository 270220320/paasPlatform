package com.uavplatform.map.dto;

import lombok.Data;

import java.util.List;

@Data
public class MapTabsMetaResponse {

    private List<MapTabMetaVO> tabs;
}
