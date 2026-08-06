package com.uavplatform.safety.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.fh2.config.Fh2Properties;
import com.uavplatform.fh2.sdk.Fh2RuntimeConfigHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对接 dji2 /safety REST（与 paasVue routeApiBaseUrl 一致），用于限飞区 upsert / 下发机场。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Dji2SafetyClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Fh2RuntimeConfigHolder configHolder;

    public boolean configured() {
        Fh2Properties cfg = configHolder.effective();
        return StringUtils.hasText(cfg.getRouteApiBase()) && StringUtils.hasText(cfg.getUserToken());
    }

    /** 创建限飞区，返回司空 flightAreaId */
    public String createArea(String name, Map<String, Object> content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("type", "nfz");
        body.put("content", content);
        body.put("createdBy", "aiflow");
        JsonNode data = post("/safety/create.rest", body);
        String id = text(data, "flightAreaId");
        if (!StringUtils.hasText(id)) {
            id = text(data, "id");
        }
        if (!StringUtils.hasText(id)) {
            throw new BizException(ErrorCodes.SAFETY_REMOTE_ERROR, "司空创建限飞区未返回 flightAreaId");
        }
        return id.trim();
    }

    /** 更新限飞区 */
    public void updateArea(String flightAreaId, String name, Map<String, Object> content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flightAreaId", flightAreaId);
        body.put("name", name);
        body.put("content", content);
        body.put("updateBy", "aiflow");
        post("/safety/update.rest", body);
    }

    /** 删除限飞区 */
    public void deleteArea(String flightAreaId) {
        if (!StringUtils.hasText(flightAreaId)) {
            return;
        }
        get("/safety/delete.rest/" + encode(flightAreaId));
    }

    /** 下发到机场设备 */
    public List<Map<String, Object>> syncToDevices(List<String> deviceSns) {
        if (deviceSns == null || deviceSns.isEmpty()) {
            return List.of();
        }
        StringBuilder q = new StringBuilder();
        for (String sn : deviceSns) {
            if (!StringUtils.hasText(sn)) {
                continue;
            }
            if (!q.isEmpty()) {
                q.append('&');
            }
            q.append("deviceSns=").append(encode(sn.trim()));
        }
        JsonNode data = get("/safety/sync.rest?" + q);
        return asObjectList(data);
    }

    /** 查询设备同步状态 */
    public List<Map<String, Object>> syncStatus(List<String> deviceSns) {
        if (deviceSns == null || deviceSns.isEmpty()) {
            return List.of();
        }
        StringBuilder q = new StringBuilder();
        for (String sn : deviceSns) {
            if (!StringUtils.hasText(sn)) {
                continue;
            }
            if (!q.isEmpty()) {
                q.append('&');
            }
            q.append("deviceSns=").append(encode(sn.trim()));
        }
        JsonNode data = get("/safety/syncstatus.rest?" + q);
        return asObjectList(data);
    }

    private JsonNode post(String path, Object body) {
        try {
            String json = JSON.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-User-Token", token())
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            return execute(request);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[dji2-safety] POST {} failed: {}", path, e.getMessage(), e);
            throw new BizException(ErrorCodes.SAFETY_REMOTE_ERROR, "司空安全区请求失败：" + e.getMessage());
        }
    }

    private JsonNode get(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-User-Token", token())
                    .GET()
                    .build();
            return execute(request);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[dji2-safety] GET {} failed: {}", path, e.getMessage(), e);
            throw new BizException(ErrorCodes.SAFETY_REMOTE_ERROR, "司空安全区请求失败：" + e.getMessage());
        }
    }

    private JsonNode execute(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException(ErrorCodes.SAFETY_REMOTE_ERROR,
                    "司空安全区 HTTP " + response.statusCode());
        }
        JsonNode root = JSON.readTree(response.body());
        int code = root.path("code").asInt(-1);
        if (code != 0) {
            String message = root.path("message").asText("司空安全区业务失败");
            throw new BizException(ErrorCodes.SAFETY_REMOTE_ERROR, message);
        }
        return root.get("data");
    }

    private URI uri(String path) {
        String base = configHolder.effective().getRouteApiBase();
        if (!StringUtils.hasText(base)) {
            throw new BizException(ErrorCodes.SAFETY_REMOTE_ERROR, "未配置 uav.fh2.route-api-base");
        }
        String normalized = base.replaceAll("/+$", "");
        String p = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalized + p);
    }

    private String token() {
        String token = configHolder.effective().getUserToken();
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCodes.SAFETY_REMOTE_ERROR, "未配置司空 userToken");
        }
        return token.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText(null);
    }

    private List<Map<String, Object>> asObjectList(JsonNode data) {
        if (data == null || data.isNull()) {
            return List.of();
        }
        try {
            if (data.isArray()) {
                return JSON.convertValue(data, new TypeReference<List<Map<String, Object>>>() {});
            }
            if (data.isObject()) {
                List<Map<String, Object>> one = new ArrayList<>();
                one.add(JSON.convertValue(data, new TypeReference<Map<String, Object>>() {}));
                return one;
            }
        } catch (Exception e) {
            log.warn("[dji2-safety] parse list failed: {}", e.getMessage());
        }
        return List.of();
    }
}
