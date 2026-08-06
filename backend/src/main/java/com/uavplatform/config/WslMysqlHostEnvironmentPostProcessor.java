package com.uavplatform.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * WSL 内 127.0.0.1 不是 Windows 宿主机。自动把 JDBC 主机改成当前默认网关（可被 MYSQL_HOST 覆盖）。
 * 须在 META-INF/spring.factories 注册；main 里也会提前 set MYSQL_HOST 兜底。
 */
public class WslMysqlHostEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(WslMysqlHostEnvironmentPostProcessor.class);
    private static final String PROPERTY_SOURCE = "wslMysqlHost";

    /** 在 SpringApplication.run 之前调用，不依赖 spring.factories。 */
    public static void applyEarlySystemProperties() {
        if (!isWsl()) {
            return;
        }
        if (firstNonBlank(System.getProperty("MYSQL_HOST"), System.getenv("MYSQL_HOST")) != null) {
            return;
        }
        String host = resolveDefaultGateway();
        if (host == null || host.isBlank()) {
            return;
        }
        System.setProperty("MYSQL_HOST", host);
        System.out.println("WSL: early MYSQL_HOST=" + host);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isWsl()) {
            return;
        }

        String host = firstNonBlank(
                environment.getProperty("MYSQL_HOST"),
                System.getProperty("MYSQL_HOST"),
                System.getenv("MYSQL_HOST"),
                resolveDefaultGateway());
        if (host == null || host.isBlank()) {
            log.warn("WSL detected but cannot resolve Windows host for MySQL (set MYSQL_HOST)");
            return;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("MYSQL_HOST", host);

        String url = safeGet(environment, "spring.datasource.url");
        if (url != null && (url.contains("://127.0.0.1:") || url.contains("://localhost:"))) {
            String rewritten = url.replace("://127.0.0.1:", "://" + host + ":")
                    .replace("://localhost:", "://" + host + ":");
            map.put("spring.datasource.url", rewritten);
            log.info("WSL: datasource host rewritten to {} (override with MYSQL_HOST)", host);
        } else {
            log.info("WSL: MYSQL_HOST={}", host);
        }

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, map));
    }

    private static String safeGet(ConfigurableEnvironment environment, String key) {
        try {
            return environment.getProperty(key);
        } catch (IllegalArgumentException ex) {
            // e.g. ${MYSQL_HOST} with no default while unset
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    static boolean isWsl() {
        try {
            String version = Files.readString(Path.of("/proc/version")).toLowerCase();
            return version.contains("microsoft") || version.contains("wsl");
        } catch (IOException ex) {
            return false;
        }
    }

    /** Parse default gateway from /proc/net/route (little-endian hex). */
    static String resolveDefaultGateway() {
        try {
            List<String> lines = Files.readAllLines(Path.of("/proc/net/route"));
            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length > 2 && "00000000".equals(parts[1])) {
                    long gw = Long.parseLong(parts[2], 16);
                    return String.format(
                            "%d.%d.%d.%d",
                            gw & 0xff,
                            (gw >> 8) & 0xff,
                            (gw >> 16) & 0xff,
                            (gw >> 24) & 0xff);
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to read default gateway: {}", ex.toString());
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
