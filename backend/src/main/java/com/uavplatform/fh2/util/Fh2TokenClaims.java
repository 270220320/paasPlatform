package com.uavplatform.fh2.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 解析司空 JWT payload（不验签），提取 organization_uuid / project_uuid。
 */
public final class Fh2TokenClaims {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Fh2TokenClaims() {
    }

    public record Claims(String organizationUuid, String projectUuid) {
    }

    public static Claims parse(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return new Claims(null, null);
        }
        try {
            String[] parts = jwt.trim().split("\\.");
            if (parts.length < 2) {
                return new Claims(null, null);
            }
            byte[] json = Base64.getUrlDecoder().decode(pad(parts[1]));
            JsonNode node = MAPPER.readTree(new String(json, StandardCharsets.UTF_8));
            String org = text(node, "organization_uuid");
            if (org == null) {
                org = text(node, "organizationUuid");
            }
            String project = text(node, "project_uuid");
            if (project == null) {
                project = text(node, "projectUuid");
            }
            return new Claims(blankToNull(org), blankToNull(project));
        } catch (Exception e) {
            return new Claims(null, null);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String pad(String b64) {
        int mod = b64.length() % 4;
        if (mod == 0) {
            return b64;
        }
        return b64 + "====".substring(mod);
    }
}
