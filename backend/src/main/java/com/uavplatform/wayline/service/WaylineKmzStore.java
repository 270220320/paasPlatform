package com.uavplatform.wayline.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 航线 KMZ 本体的本地磁盘存取（私有化部署无 OSS，落 storage-dir）。
 *
 * <p>storage_key 形如 import/{uuid}.kmz，磁盘路径 = storage-dir / storage_key；
 * 读取时校验归一化路径仍在 storage-dir 内，防路径穿越。
 */
@Slf4j
@Component
public class WaylineKmzStore {

    private static final String KEY_PREFIX = "import/";

    private final Path baseDir;

    public WaylineKmzStore(@Value("${uav.wayline.storage-dir:./data/waylines}") String storageDir) {
        this.baseDir = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir.resolve(KEY_PREFIX));
        } catch (IOException e) {
            log.warn("[wayline] KMZ 存储目录不可写：{}（{}）", baseDir, e.getMessage());
        }
    }

    /** 保存 KMZ 字节，返回 storage_key */
    public String save(byte[] data) {
        String key = KEY_PREFIX + UUID.randomUUID() + ".kmz";
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("KMZ 文件保存失败：" + e.getMessage(), e);
        }
    }

    /** 读取 KMZ 字节；key 非法或文件不存在返回 null（调用方降级导出 JSON） */
    public byte[] open(String storageKey) {
        Path target = resolve(storageKey);
        if (target == null || !Files.isRegularFile(target)) {
            return null;
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            log.warn("[wayline] KMZ 文件读取失败 key={}: {}", storageKey, e.getMessage());
            return null;
        }
    }

    /** KMZ 文件绝对路径（供 SDK importKmz 直接读文件）；不存在返回 null */
    public String pathOf(String storageKey) {
        Path target = resolve(storageKey);
        return target != null && Files.isRegularFile(target) ? target.toString() : null;
    }

    /** 删除 KMZ 文件（失败仅记日志，不阻塞航线删除） */
    public void delete(String storageKey) {
        Path target = resolve(storageKey);
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("[wayline] KMZ 文件删除失败 key={}: {}", storageKey, e.getMessage());
        }
    }

    private Path resolve(String storageKey) {
        if (!StringUtils.hasText(storageKey) || !storageKey.startsWith(KEY_PREFIX)) {
            return null;
        }
        Path target = baseDir.resolve(storageKey).normalize();
        if (!target.startsWith(baseDir)) {
            log.warn("[wayline] 非法 storage_key（疑似路径穿越）：{}", storageKey);
            return null;
        }
        return target;
    }
}
