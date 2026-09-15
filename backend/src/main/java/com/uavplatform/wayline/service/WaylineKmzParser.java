package com.uavplatform.wayline.service;

import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DJI 航线 KMZ（zip 包，内含 wpmz/ 目录）解析：结构校验 + 元数据尽力提取。
 *
 * <p>结构规则（对应 TC-WAY-005）：非 zip 或缺少 wpmz/ 下 template.kml、waylines.wpml 均视为解析失败（40003）。
 * 元数据（航线名、航点数）提取失败不阻塞导入，逐级回退。
 */
public final class WaylineKmzParser {

    /** 单个 zip 条目解压上限，防解压炸弹 */
    private static final int MAX_ENTRY_BYTES = 32 * 1024 * 1024;
    private static final String TEMPLATE_KML = "wpmz/template.kml";

    private WaylineKmzParser() {
    }

    /** 解析结果：name 为 null 表示未提取到（调用方回退文件名） */
    public record ParsedKmz(String name, Integer waypointCount) {
    }

    public static ParsedKmz parse(byte[] kmz, String fallbackName) {
        Map<String, byte[]> entries = unzip(kmz);
        boolean hasWpmz = entries.keySet().stream().anyMatch(n -> n.startsWith("wpmz/"));
        if (!hasWpmz) {
            throw new BizException(ErrorCodes.WAYLINE_PARSE_FAIL, "KMZ 解析失败：缺少 wpmz 目录");
        }
        boolean hasStruct = entries.containsKey(TEMPLATE_KML) || entries.containsKey("wpmz/waylines.wpml");
        if (!hasStruct) {
            throw new BizException(ErrorCodes.WAYLINE_PARSE_FAIL, "KMZ 解析失败：wpmz 下缺少 template.kml / waylines.wpml");
        }

        byte[] kml = entries.get(TEMPLATE_KML);
        String name = kml != null ? extractName(new String(kml, StandardCharsets.UTF_8)) : null;
        if (!StringUtils.hasText(name)) {
            name = fallbackName;
        }
        Integer waypointCount = kml != null ? countPlacemarks(new String(kml, StandardCharsets.UTF_8)) : null;
        return new ParsedKmz(name, waypointCount);
    }

    /** zip 遍历失败（非 zip、损坏）统一抛 40003 */
    private static Map<String, byte[]> unzip(byte[] kmz) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(kmz))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = zis.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > MAX_ENTRY_BYTES) {
                        throw new BizException(ErrorCodes.WAYLINE_PARSE_FAIL, "KMZ 解析失败：文件内容超出上限");
                    }
                }
                entries.put(normalize(entry.getName()), buf.toByteArray());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCodes.WAYLINE_PARSE_FAIL, "KMZ 解析失败：非法的 zip 结构");
        }
        return entries;
    }

    /** zip 条目名统一小写、分隔符归一，规避大小写差异（template.KML 等） */
    private static String normalize(String raw) {
        return raw.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    /** 提取 KML 中 Document 下的航线名：取第一个 <name> 文本（Document 的 name 位于文件最前） */
    private static String extractName(String kml) {
        int open = kml.indexOf("<name>");
        if (open < 0) {
            return null;
        }
        int close = kml.indexOf("</name>", open);
        if (close < 0) {
            return null;
        }
        String name = kml.substring(open + 6, close).trim();
        if (name.isEmpty()) {
            return null;
        }
        return decodeXmlEntities(name);
    }

    /** template.kml 每个航点一个 Placemark，按出现次数粗略统计 */
    private static Integer countPlacemarks(String kml) {
        int count = 0;
        int idx = 0;
        while ((idx = kml.indexOf("<Placemark", idx)) >= 0) {
            count++;
            idx += 10;
        }
        return count > 0 ? count : null;
    }

    private static String decodeXmlEntities(String s) {
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
