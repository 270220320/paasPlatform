package com.uavplatform.wayline.dto;

/**
 * 航线导出载体：KMZ 优先，无本体时降级 JSON。
 */
public record WaylineExportFile(byte[] data, String filename, String contentType) {

    public static final String TYPE_KMZ = "application/vnd.google-earth.kmz";
    public static final String TYPE_JSON = "application/json";
}
