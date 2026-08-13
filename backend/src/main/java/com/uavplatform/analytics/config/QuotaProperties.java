package com.uavplatform.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 首页 / 资源库配额展示策略。
 * <p>
 * 公有云司空有「存储 / 建图张数 / 直播分钟」套餐；私有化部署通常无此计量，
 * 种子表里的 5GB / 1000 张 / 10000 分钟只是仿标准版占位，不能当真实上限。
 */
@Data
@Component
@ConfigurationProperties(prefix = "uav.quota")
public class QuotaProperties {

    /**
     * private：私有化（默认）——存储/建图上限按「不限」展示，直播未统计；
     * saas：沿用 t_workspace_quota 套餐数字。
     */
    private String mode = "private";

    /**
     * 私有化时可配置真实磁盘/对象存储容量（字节）。0 或空 = 不限。
     * 仅 mode=private 时生效，覆盖库表 storage_total_bytes。
     */
    private Long storageTotalBytes = 0L;

    public boolean isPrivate() {
        return mode == null || mode.isBlank() || "private".equalsIgnoreCase(mode.trim());
    }
}
