package com.uavplatform.chat.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 对话控制模块配置（本地 Ollama + qwen 模型）。
 *
 * <p>后端在 WSL 中运行、Ollama 在 Windows 宿主机时的接法：
 * <ol>
 *   <li>Windows 侧设置环境变量 {@code OLLAMA_HOST=0.0.0.0:11434} 后重启 Ollama（防火墙放行 11434）；</li>
 *   <li>WSL 侧可 {@code export OLLAMA_BASE_URL=http://<WSL网关IP>:11434}；
 *       未导出时本类会在启动后自动探测：127.0.0.1 不通则解析默认网关（/proc/net/route）并切换，
 *       因此非交互 shell / IDE / nohup 等无环境变量的启动方式也能正常工作。</li>
 * </ol>
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "uav.chat")
public class ChatProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** Ollama 服务地址（yml 占位符 ${OLLAMA_BASE_URL:http://127.0.0.1:11434}；WSL 下由 resolveBaseUrlForWsl 自动纠正） */
    private String baseUrl = "http://127.0.0.1:11434";

    /** 模型名；留空时自动取 /api/tags 中第一个名称含 qwen 的模型 */
    private String model = "";

    /** 采样温度 */
    private double temperature = 0.6;

    /** 是否开启思考模式（qwen3 系列 think:false 可加速工具调用场景） */
    private boolean thinkEnabled = false;

    /** 单次提问内 Agent 工具循环最大轮数 */
    private int maxTurns = 6;

    /** Ollama HTTP 请求超时（秒） */
    private int requestTimeoutSeconds = 120;

    /** 安全等级上限：1=只读查询 2=常规控制（生产推荐） 3=含紧急/危险操作（演示） */
    private int maxSecurityLevel = 3;

    /** L2/L3 待确认动作有效期（分钟） */
    private int pendingActionTtlMinutes = 5;

    /** 会话历史携带给模型的最大消息条数 */
    private int historyWindow = 20;

    /** 查询类工具历史结果的有效期（分钟）：过期后不再作为事实拼入上下文，促使模型重新调工具 */
    private int historyToolTtlMinutes = 5;

    /** 追加到系统提示词的自定义内容（可选） */
    private String extraSystemPrompt = "";

    /** 启动探测均失败时记下的网关候选，供运行期自愈重试；null=无需重试（显式配置/已可达） */
    private volatile String gatewayCandidate = null;

    /** 上次自愈探测时间戳（ms），节流用 */
    private volatile long lastHealProbeAt = 0L;

    /**
     * WSL/容器内启动且未显式配置 OLLAMA_BASE_URL 时自动定位宿主机 Ollama：
     * 127.0.0.1 探测不通 → 解析默认网关 → 探测网关:11434，可达则切换。
     * 在 @ConfigurationProperties 绑定完成后执行，可覆盖 yml 默认值。
     * 两处均不可达（如 Ollama 尚未启动）时记下网关候选，由 ensureReachable 运行期自愈。
     */
    @PostConstruct
    void resolveBaseUrlForWsl() {
        if (System.getenv("OLLAMA_BASE_URL") != null) {
            return; // 显式指定，尊重配置
        }
        if (probe(baseUrl)) {
            return; // 默认地址本身可达（Windows 直跑等场景）
        }
        String gateway = defaultGateway();
        if (gateway == null) {
            return;
        }
        String candidate = "http://" + gateway + ":11434";
        if (probe(candidate)) {
            log.info("[chat] ollama base-url 自动切换为宿主机网关: {}（原 {} 不可达）", candidate, baseUrl);
            baseUrl = candidate;
        } else {
            gatewayCandidate = candidate;
            log.warn("[chat] ollama 不可达：{} 与网关 {} 均探测失败，待 Ollama 启动后自动重试（检查 Windows 侧 OLLAMA_HOST/防火墙）",
                    baseUrl, candidate);
        }
    }

    /**
     * 运行期自愈：启动时两处均探测失败的场景，Ollama 后续启动了也能自动切换，无需重启后端。
     * 由 OllamaClient 请求入口触发；30 秒节流避免每次请求都探测。
     */
    public synchronized void ensureReachable() {
        if (gatewayCandidate == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHealProbeAt < 30_000L) {
            return;
        }
        lastHealProbeAt = now;
        if (probe(baseUrl)) {
            // 原地址恢复了（如 Ollama 改回本机监听），清候选停止重试
            gatewayCandidate = null;
            return;
        }
        if (probe(gatewayCandidate)) {
            log.info("[chat] ollama base-url 运行期自愈切换: {}（原 {} 不可达）", gatewayCandidate, baseUrl);
            baseUrl = gatewayCandidate;
            gatewayCandidate = null;
        }
    }

    private static boolean probe(String base) {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<Void> resp = http.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/tags"))
                            .timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** /proc/net/route 默认路由网关（小端 hex → 点分 IP）；非 Linux 或无默认路由返回 null */
    private static String defaultGateway() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/net/route"))) {
                String[] f = line.trim().split("\\s+");
                if (f.length > 2 && "00000000".equals(f[1])
                        && f[2].matches("[0-9A-Fa-f]{8}") && !"00000000".equals(f[2])) {
                    StringBuilder ip = new StringBuilder(15);
                    for (int i = 3; i >= 0; i--) {
                        if (ip.length() > 0) {
                            ip.append('.');
                        }
                        ip.append(Integer.parseInt(f[2].substring(i * 2, i * 2 + 2), 16));
                    }
                    return ip.toString();
                }
            }
        } catch (Exception ignored) {
            // 非 Linux / 无该文件，忽略
        }
        return null;
    }
}
