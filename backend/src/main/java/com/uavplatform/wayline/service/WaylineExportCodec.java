package com.uavplatform.wayline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.wayline.domain.WaylineContentEntity;
import com.uavplatform.wayline.domain.WaylineEntity;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 航线导出 JSON 封装编解码：无 KMZ 本体航线（司空镜像等）的备份迁移格式，可再次导入恢复。
 *
 * <p>结构：{ format: "uav-wayline-export", version: 1, exportedAt, wayline: {元数据}, structuredParams? }
 * 旧版 txt 导出无 format 标记，导入时直接拒收（40003）。
 */
public final class WaylineExportCodec {

    public static final String FORMAT = "uav-wayline-export";
    private static final int VERSION = 1;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WaylineExportCodec() {
    }

    /** 导入恢复内容：structuredParams 可为 null（纯元数据备份）；fh2WaylineUuid/status 供镜像恢复回写 */
    public record Decoded(String name, String aircraftModel, String waylineType, String structuredParams,
                          String fh2WaylineUuid, String status) {
    }

    public static String encode(WaylineEntity e, WaylineContentEntity c) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("exportedAt", LocalDateTime.now().format(FMT));
        ObjectNode w = root.putObject("wayline");
        w.put("name", e.getName());
        w.put("aircraftModel", e.getAircraftModel());
        w.put("waylineType", e.getWaylineType());
        w.put("status", e.getStatus());
        if (StringUtils.hasText(e.getFh2WaylineUuid())) {
            w.put("fh2WaylineUuid", e.getFh2WaylineUuid());
        }
        if (e.getUpdatedAt() != null) {
            w.put("updatedAt", e.getUpdatedAt().format(FMT));
        }
        if (c != null && StringUtils.hasText(c.getStructuredParams())) {
            root.put("structuredParams", c.getStructuredParams());
        }
        return root.toString();
    }

    /** 非法 JSON 或缺少 format 标记统一 40003 */
    public static Decoded decode(byte[] data, String fallbackName) {
        JsonNode root;
        try {
            root = MAPPER.readTree(new String(data, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new BizException(ErrorCodes.WAYLINE_PARSE_FAIL, "导入失败：非 KMZ 也非有效的航线导出 JSON");
        }
        if (!FORMAT.equals(root.path("format").asText(null))) {
            throw new BizException(ErrorCodes.WAYLINE_PARSE_FAIL, "导入失败：JSON 缺少 " + FORMAT + " 格式标记");
        }
        JsonNode w = root.path("wayline");
        String name = StringUtils.hasText(w.path("name").asText(null)) ? w.path("name").asText() : fallbackName;
        return new Decoded(name,
                w.path("aircraftModel").asText(null),
                w.path("waylineType").asText(null),
                root.hasNonNull("structuredParams") ? root.get("structuredParams").asText() : null,
                w.hasNonNull("fh2WaylineUuid") ? w.get("fh2WaylineUuid").asText() : null,
                w.hasNonNull("status") ? w.get("status").asText() : null);
    }
}
