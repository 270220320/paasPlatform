package com.uavplatform.wayline.service;

import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaylineKmzParserTest {

    @Test
    void parse_notZip_shouldFail() {
        BizException ex = assertThrows(BizException.class,
                () -> WaylineKmzParser.parse("not a zip".getBytes(StandardCharsets.UTF_8), "fb"));
        assertEquals(ErrorCodes.WAYLINE_PARSE_FAIL, ex.getCode());
    }

    @Test
    void parse_missingWpmz_shouldFail() {
        byte[] zip = zipOf("readme.txt", "hello");
        BizException ex = assertThrows(BizException.class, () -> WaylineKmzParser.parse(zip, "fb"));
        assertEquals(ErrorCodes.WAYLINE_PARSE_FAIL, ex.getCode());
    }

    @Test
    void parse_wpmzWithoutStruct_shouldFail() {
        byte[] zip = zipOf("wpmz/res/logo.png", "png");
        BizException ex = assertThrows(BizException.class, () -> WaylineKmzParser.parse(zip, "fb"));
        assertEquals(ErrorCodes.WAYLINE_PARSE_FAIL, ex.getCode());
    }

    @Test
    void parse_ok_shouldExtractNameAndCount() {
        String kml = "<kml><Document><name>航線&amp;B</name>"
                + "<Placemark></Placemark><Placemark></Placemark><Placemark></Placemark>"
                + "</Document></kml>";
        WaylineKmzParser.ParsedKmz parsed = WaylineKmzParser.parse(zipOf("wpmz/template.kml", kml), "fb");

        assertEquals("航線&B", parsed.name());
        assertEquals(3, parsed.waypointCount());
    }

    @Test
    void parse_kmlWithoutName_shouldFallback() {
        String kml = "<kml><Document><Placemark/></Document></kml>";
        WaylineKmzParser.ParsedKmz parsed = WaylineKmzParser.parse(zipOf("wpmz/template.kml", kml), "文件名");

        assertEquals("文件名", parsed.name());
        assertEquals(1, parsed.waypointCount());
    }

    @Test
    void parse_onlyWpml_shouldPassWithFallbackName() {
        byte[] zip = zipOf("wpmz/waylines.wpml", "<wpml:mission/>");
        WaylineKmzParser.ParsedKmz parsed = WaylineKmzParser.parse(zip, "wp");

        assertEquals("wp", parsed.name());
        assertNull(parsed.waypointCount());
    }

    private static byte[] zipOf(String entryName, String content) {
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(buf)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            return buf.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
