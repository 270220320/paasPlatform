package com.uavplatform.chat.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uavplatform.chat.config.ChatProperties;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地 Ollama 客户端（/api/chat 非流式 + tools，/api/tags 探活）。
 * qwen3 系列在 Ollama 0.9+ 支持 think 参数与原生 tool calling。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChatProperties properties;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 单轮补全结果 */
    public record Completion(String content, String thinking, List<ToolCall> toolCalls) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    /** 工具调用请求 */
    public record ToolCall(String name, JsonNode arguments) {
    }

    /**
     * 非流式对话补全。messages 为 ollama 原生格式（role/content/tool_name），
     * tools 为 [{"type":"function","function":{name,description,parameters}}]。
     */
    public Completion chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", resolveModel());
        body.set("messages", JSON.valueToTree(messages));
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", JSON.valueToTree(tools));
        }
        body.put("stream", false);
        body.put("think", properties.isThinkEnabled());
        ObjectNode options = body.putObject("options");
        options.put("temperature", properties.getTemperature());

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedBase() + "/api/chat"))
                    .timeout(Duration.ofSeconds(Math.max(30, properties.getRequestTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ErrorCodes.CHAT_LLM_ERROR,
                        "Ollama HTTP " + response.statusCode() + ": " + truncate(response.body()));
            }
            JsonNode root = JSON.readTree(response.body());
            if (root.path("error").isTextual() && StringUtils.hasText(root.path("error").asText())) {
                throw new BizException(ErrorCodes.CHAT_LLM_ERROR, "Ollama: " + root.path("error").asText());
            }
            JsonNode message = root.path("message");
            String content = message.path("content").asText("");
            String thinking = message.path("thinking").asText(null);
            List<ToolCall> calls = new ArrayList<>();
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode call : toolCalls) {
                    JsonNode fn = call.path("function");
                    String name = fn.path("name").asText(null);
                    if (!StringUtils.hasText(name)) {
                        continue;
                    }
                    JsonNode args = fn.path("arguments");
                    // 兼容 arguments 为 JSON 字符串的返回格式
                    if (args.isTextual()) {
                        try {
                            args = JSON.readTree(args.asText());
                        } catch (Exception e) {
                            log.warn("[chat] tool arguments parse failed tool={} raw={}", name, args.asText());
                            args = JSON.createObjectNode();
                        }
                    }
                    if (args == null || args.isNull() || !args.isObject()) {
                        args = JSON.createObjectNode();
                    }
                    calls.add(new ToolCall(name, args));
                }
            }
            return new Completion(content, thinking, calls);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[chat] ollama /api/chat failed: {}", e.getMessage(), e);
            throw new BizException(ErrorCodes.CHAT_LLM_ERROR,
                    "Ollama 调用失败（检查服务是否启动、OLLAMA_BASE_URL 是否可达）：" + e.getMessage());
        }
    }

    /** /api/tags 模型列表 */
    public List<String> listModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedBase() + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return List.of();
            }
            List<String> models = new ArrayList<>();
            JsonNode arr = JSON.readTree(response.body()).path("models");
            if (arr.isArray()) {
                arr.forEach(m -> {
                    String name = m.path("name").asText(null);
                    if (StringUtils.hasText(name)) {
                        models.add(name);
                    }
                });
            }
            return models;
        } catch (Exception e) {
            return List.of();
        }
    }

    public boolean reachable() {
        return !listModels().isEmpty();
    }

    /** 解析生效模型：显式配置优先，否则取第一个名称含 qwen 的模型 */
    public String resolveModel() {
        if (StringUtils.hasText(properties.getModel())) {
            return properties.getModel().trim();
        }
        List<String> models = listModels();
        if (models.isEmpty()) {
            throw new BizException(ErrorCodes.CHAT_LLM_ERROR,
                    "Ollama 不可达或无可用模型（base-url=" + properties.getBaseUrl() + "）");
        }
        return models.stream().filter(m -> m.toLowerCase().contains("qwen")).findFirst()
                .orElse(models.get(0));
    }

    /** 构建 ollama tools 数组元素 */
    public static Map<String, Object> toolSchema(String name, String description, Map<String, Object> parameters) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters));
    }

    private String normalizedBase() {
        // 先试运行期自愈：启动时 Ollama 未就绪的场景，就绪后自动切换 base-url
        properties.ensureReachable();
        String base = properties.getBaseUrl();
        if (!StringUtils.hasText(base)) {
            throw new BizException(ErrorCodes.CHAT_LLM_ERROR, "未配置 uav.chat.base-url");
        }
        return base.replaceAll("/+$", "");
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }
}
