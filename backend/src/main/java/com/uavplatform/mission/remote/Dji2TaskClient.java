package com.uavplatform.mission.remote;

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
 * 对接 dji2 飞行任务 REST（与 paasVue routeApiBaseUrl 一致）。
 * <ul>
 *   <li>{@code /flighttask/*}：立即/条件单次任务，创建直接返回 uuid</li>
 *   <li>{@code /task/*}：飞行计划（立即/周期）及子任务</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Dji2TaskClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Fh2RuntimeConfigHolder configHolder;

    public boolean configured() {
        Fh2Properties cfg = configHolder.effective();
        return StringUtils.hasText(cfg.getRouteApiBase()) && StringUtils.hasText(cfg.getUserToken());
    }

    /** 创建飞行任务（立即/条件），返回任务 uuid */
    public String createFlightTask(Map<String, Object> body) {
        JsonNode data = post("/flighttask/create.rest", body);
        String uuid = text(data, "uuid");
        if (!StringUtils.hasText(uuid) && data != null && data.isTextual()) {
            uuid = data.asText();
        }
        if (!StringUtils.hasText(uuid)) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "司空创建飞行任务未返回 uuid");
        }
        return uuid.trim();
    }

    public Map<String, Object> getFlightTaskDetail(String taskUuid) {
        if (!StringUtils.hasText(taskUuid)) {
            return Map.of();
        }
        JsonNode data = get("/flighttask/detail.rest/" + encode(taskUuid.trim()));
        return asObject(data);
    }

    public void deleteFlightTask(String taskUuid) {
        if (!StringUtils.hasText(taskUuid)) {
            return;
        }
        get("/flighttask/delete.rest/" + encode(taskUuid.trim()));
    }

    /** 创建飞行计划（立即 taskType=1 / 周期 taskType=2），成功时 data 常为 null */
    public void createTaskPlan(Map<String, Object> body) {
        post("/task/create.rest", body);
    }

    public List<Map<String, Object>> listTaskPlans(Map<String, Object> body) {
        JsonNode data = post("/task/list.rest", body);
        return asObjectList(data);
    }

    public List<Map<String, Object>> listTaskSubs(long taskId, int pageNo, int pageCount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pageNo", pageNo);
        body.put("pageCount", pageCount);
        body.put("taskId", taskId);
        JsonNode data = post("/task/sub/list.rest", body);
        return asObjectList(data);
    }

    public Map<String, Object> getTaskSubDetail(String taskUuid) {
        if (!StringUtils.hasText(taskUuid)) {
            return Map.of();
        }
        JsonNode data = get("/task/sub/detail.rest/" + encode(taskUuid.trim()));
        return asObject(data);
    }

    /**
     * 按架次/任务 UUID 查媒体列表（私有化 dji2）。
     * 返回项通常含 {@code filePath}（如 {@code 60/65/66}）：倒数第二段为飞行任务资源夹 ID，
     * 末段多为可见光等子目录（三维重建应使用任务夹，勿用末段）。
     */
    public List<Map<String, Object>> listMediaByFlightId(String flightId, int pageNo, int pageCount) {
        if (!StringUtils.hasText(flightId)) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pageNo", Math.max(pageNo, 1));
        body.put("pageCount", Math.min(Math.max(pageCount, 1), 200));
        body.put("flightId", flightId.trim());
        JsonNode data = post("/media/list.rest", body);
        return asObjectList(data);
    }

    public void deleteTaskSub(String taskUuid) {
        if (!StringUtils.hasText(taskUuid)) {
            return;
        }
        get("/task/sub/delete.rest/" + encode(taskUuid.trim()));
    }

    private JsonNode post(String path, Object body) {
        try {
            String json = JSON.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("X-User-Token", token())
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            return execute(request);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[dji2-task] POST {} failed: {}", path, e.getMessage(), e);
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "司空飞行任务请求失败：" + e.getMessage());
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
            log.error("[dji2-task] GET {} failed: {}", path, e.getMessage(), e);
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "司空飞行任务请求失败：" + e.getMessage());
        }
    }

    private JsonNode execute(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR,
                    "司空飞行任务 HTTP " + response.statusCode());
        }
        JsonNode root = JSON.readTree(response.body());
        int code = root.path("code").asInt(-1);
        if (code != 0) {
            String message = root.path("message").asText("司空飞行任务业务失败");
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, message);
        }
        return root.get("data");
    }

    private URI uri(String path) {
        String base = configHolder.effective().getRouteApiBase();
        if (!StringUtils.hasText(base)) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "未配置 uav.fh2.route-api-base");
        }
        String normalized = base.replaceAll("/+$", "");
        String p = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalized + p);
    }

    private String token() {
        String token = configHolder.effective().getUserToken();
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCodes.MISSION_REMOTE_ERROR, "未配置司空 userToken");
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

    private Map<String, Object> asObject(JsonNode data) {
        if (data == null || data.isNull() || !data.isObject()) {
            return Map.of();
        }
        try {
            return JSON.convertValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[dji2-task] parse object failed: {}", e.getMessage());
            return Map.of();
        }
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
                // 部分接口包在 list/records 里
                if (data.has("list") && data.get("list").isArray()) {
                    return JSON.convertValue(data.get("list"), new TypeReference<List<Map<String, Object>>>() {});
                }
                if (data.has("records") && data.get("records").isArray()) {
                    return JSON.convertValue(data.get("records"), new TypeReference<List<Map<String, Object>>>() {});
                }
                List<Map<String, Object>> one = new ArrayList<>();
                one.add(JSON.convertValue(data, new TypeReference<Map<String, Object>>() {}));
                return one;
            }
        } catch (Exception e) {
            log.warn("[dji2-task] parse list failed: {}", e.getMessage());
        }
        return List.of();
    }
}
