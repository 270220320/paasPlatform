package com.uavplatform.wayline.service;

import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.fh2.dto.Fh2KmzUploadResult;
import com.uavplatform.fh2.dto.Fh2WaylineImportedEvent;
import com.uavplatform.fh2.dto.Fh2WaylineItemVO;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import com.uavplatform.mission.mapper.MissionPlanMapper;
import com.uavplatform.wayline.domain.WaylineContentEntity;
import com.uavplatform.wayline.domain.WaylineEntity;
import com.uavplatform.wayline.dto.WaylineCreateRequest;
import com.uavplatform.wayline.dto.WaylineDetailVO;
import com.uavplatform.wayline.dto.WaylineExportFile;
import com.uavplatform.wayline.mapper.WaylineContentMapper;
import com.uavplatform.wayline.mapper.WaylineFolderMapper;
import com.uavplatform.wayline.mapper.WaylineMapper;
import com.uavplatform.wayline.mapper.WaylineSnapshotMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class WaylineServiceTest {

    @Mock
    private WaylineMapper waylineMapper;
    @Mock
    private WaylineContentMapper waylineContentMapper;
    @Mock
    private WaylineFolderMapper waylineFolderMapper;
    @Mock
    private WaylineSnapshotMapper waylineSnapshotMapper;
    @Mock
    private MissionPlanMapper missionPlanMapper;
    @Mock
    private Fh2SdkClient fh2SdkClient;
    @Mock
    private WaylineKmzStore kmzStore;
    @Mock
    private com.uavplatform.notification.service.NotificationService notificationService;

    private WaylineService waylineService;

    @BeforeEach
    void setUp() {
        waylineService = new WaylineService(
                waylineMapper, waylineContentMapper, waylineFolderMapper,
                waylineSnapshotMapper, missionPlanMapper, fh2SdkClient, kmzStore, notificationService);
        LoginUser user = new LoginUser();
        user.setPermissions(List.of("wayline:read", "wayline:write"));
        UserContext.set(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void create_unsupportedType_shouldFail() {
        WaylineCreateRequest req = new WaylineCreateRequest();
        req.setName("test");
        req.setAircraftModel("M350 RTK");
        req.setWaylineType("slope");

        BizException ex = assertThrows(BizException.class, () -> waylineService.create(req));
        assertEquals(ErrorCodes.WAYLINE_TYPE_UNSUPPORTED, ex.getCode());
    }

    // ---- U-032 导入导出 ----

    @Test
    void import_missingWpmz_shouldFailWith40003() {
        MockMultipartFile file = new MockMultipartFile("file", "bad.kmz",
                "application/vnd.google-earth.kmz", zipOf("readme.txt", "not a kmz"));

        BizException ex = assertThrows(BizException.class,
                () -> waylineService.importFile(file, 1L, null));
        assertEquals(ErrorCodes.WAYLINE_PARSE_FAIL, ex.getCode());
    }

    @Test
    void import_plainText_shouldFailWith40003() {
        MockMultipartFile file = new MockMultipartFile("file", "old-export.txt",
                "text/plain", "旧版 txt 导出内容".getBytes(StandardCharsets.UTF_8));

        BizException ex = assertThrows(BizException.class,
                () -> waylineService.importFile(file, 1L, null));
        assertEquals(ErrorCodes.WAYLINE_PARSE_FAIL, ex.getCode());
    }

    @Test
    void import_exportJson_shouldRestoreWayline() {
        String json = "{\"format\":\"uav-wayline-export\",\"version\":1"
                + ",\"wayline\":{\"name\":\"备份航线\",\"aircraftModel\":\"M30T\",\"waylineType\":\"grid_2d\",\"status\":\"draft\"}"
                + ",\"structuredParams\":\"{\\\"k\\\":1}\"}";
        MockMultipartFile file = new MockMultipartFile("file", "backup.json",
                "application/json", json.getBytes(StandardCharsets.UTF_8));

        when(waylineMapper.insert(any(WaylineEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, WaylineEntity.class).setId(200L);
            return 1;
        });
        when(waylineContentMapper.insert(any(WaylineContentEntity.class))).thenReturn(1);

        WaylineDetailVO vo = waylineService.importFile(file, 1L, null);

        assertEquals("备份航线", vo.getName());
        assertEquals("M30T", vo.getAircraftModel());
        assertEquals("grid_2d", vo.getWaylineType());
        assertEquals("{\"k\":1}", vo.getContent().getStructuredParams());
        // JSON 恢复链无 KMZ 本体
        assertNull(vo.getContent().getStorageKey());
    }

    @Test
    void import_ok_shouldUseKmlNameAndStoreKmz() {
        String kml = "<kml><Document><name>测试航线</name>"
                + "<Placemark>" + "<Placemark>"
                + "</Document></kml>";
        byte[] kmz = zipOf("wpmz/template.kml", kml);
        MockMultipartFile file = new MockMultipartFile("file", "source.kmz",
                "application/vnd.google-earth.kmz", kmz);

        when(kmzStore.save(any(byte[].class))).thenReturn("import/uuid-1.kmz");
        when(waylineMapper.insert(any(WaylineEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, WaylineEntity.class).setId(100L);
            return 1;
        });
        when(waylineContentMapper.insert(any(WaylineContentEntity.class))).thenReturn(1);
        when(waylineContentMapper.selectById(100L)).thenReturn(new WaylineContentEntity());
        when(waylineContentMapper.updateById(any(WaylineContentEntity.class))).thenReturn(1);

        WaylineDetailVO vo = waylineService.importFile(file, 1L, null);

        assertEquals("测试航线", vo.getName());
        assertEquals("M350 RTK", vo.getAircraftModel());
        assertEquals("import/uuid-1.kmz", vo.getContent().getStorageKey());
        assertNotNull(vo.getContent().getChecksum());
        assertTrue(vo.getContent().getStructuredParams().contains("\"waypointCount\":2"));
    }

    @Test
    void export_withLocalKmz_shouldReturnKmz() {
        WaylineEntity entity = wayline(1L, "巡检航线");
        when(waylineMapper.selectById(1L)).thenReturn(entity);
        WaylineContentEntity content = new WaylineContentEntity();
        content.setStorageKey("import/uuid-1.kmz");
        when(waylineContentMapper.selectById(1L)).thenReturn(content);
        byte[] kmz = zipOf("wpmz/template.kml", "<kml><Document><name>x</name></Document></kml>");
        when(kmzStore.open("import/uuid-1.kmz")).thenReturn(kmz);

        WaylineExportFile file = waylineService.export(1L);

        assertEquals(WaylineExportFile.TYPE_KMZ, file.contentType());
        assertEquals("巡检航线.kmz", file.filename());
        assertArrayEquals(kmz, file.data());
    }

    @Test
    void export_noKmz_shouldFallbackExportJson() {
        when(waylineMapper.selectById(1L)).thenReturn(wayline(1L, "巡检航线"));
        when(waylineContentMapper.selectById(1L)).thenReturn(null);

        WaylineExportFile file = waylineService.export(1L);

        assertEquals(WaylineExportFile.TYPE_JSON, file.contentType());
        assertTrue(file.filename().endsWith(".json"));
        // 封装格式可再导入
        assertTrue(new String(file.data(), StandardCharsets.UTF_8).contains("\"format\":\"uav-wayline-export\""));
        assertTrue(new String(file.data(), StandardCharsets.UTF_8).contains("巡检航线"));
    }

    @Test
    void export_thenImport_shouldRoundtrip() {
        when(waylineMapper.selectById(1L)).thenReturn(wayline(1L, "巡检航线"));
        WaylineContentEntity content = new WaylineContentEntity();
        content.setStructuredParams("{\"points\":[1,2]}");
        when(waylineContentMapper.selectById(1L)).thenReturn(content);

        WaylineExportFile exported = waylineService.export(1L);
        MockMultipartFile file = new MockMultipartFile("file", "backup.json",
                "application/json", exported.data());

        when(waylineMapper.insert(any(WaylineEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, WaylineEntity.class).setId(300L);
            return 1;
        });
        when(waylineContentMapper.insert(any(WaylineContentEntity.class))).thenReturn(1);

        WaylineDetailVO restored = waylineService.importFile(file, 1L, null);

        assertEquals("巡检航线", restored.getName());
        assertEquals("M350 RTK", restored.getAircraftModel());
        assertEquals("{\"points\":[1,2]}", restored.getContent().getStructuredParams());
    }

    @Test
    void import_exportJson_withFh2Uuid_shouldRestoreUuidForEditing() {
        // 备份的原记录已删（本地无同 uuid）→ 新建并回写 fh2WaylineUuid，恢复后可在编辑器打开
        String json = "{\"format\":\"uav-wayline-export\",\"version\":1"
                + ",\"wayline\":{\"name\":\"镜像备份\",\"aircraftModel\":\"M30T\",\"waylineType\":\"waypoint\""
                + ",\"status\":\"published\",\"fh2WaylineUuid\":\"uuid-fh2-1\"}"
                + ",\"structuredParams\":\"{\\\"k\\\":2}\"}";
        MockMultipartFile file = new MockMultipartFile("file", "mirror.json",
                "application/json", json.getBytes(StandardCharsets.UTF_8));

        when(waylineMapper.selectOne(any())).thenReturn(null);
        when(waylineMapper.insert(any(WaylineEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, WaylineEntity.class).setId(400L);
            return 1;
        });
        when(waylineContentMapper.insert(any(WaylineContentEntity.class))).thenReturn(1);

        WaylineDetailVO vo = waylineService.importFile(file, 1L, null);

        assertEquals("镜像备份", vo.getName());
        ArgumentCaptor<WaylineEntity> captor = ArgumentCaptor.forClass(WaylineEntity.class);
        verify(waylineMapper).updateById(captor.capture());
        assertEquals(400L, captor.getValue().getId());
        assertEquals("uuid-fh2-1", captor.getValue().getFh2WaylineUuid());
    }

    @Test
    void import_exportJson_withExistingUuid_shouldUpdateExistingRecord() {
        // 备份的原记录还在 → 恢复更新原记录而非新建，避免同一司空航线双份镜像
        String json = "{\"format\":\"uav-wayline-export\",\"version\":1"
                + ",\"wayline\":{\"name\":\"镜像恢复\",\"aircraftModel\":\"M30T\",\"waylineType\":\"waypoint\""
                + ",\"status\":\"draft\",\"fh2WaylineUuid\":\"uuid-fh2-1\"}"
                + ",\"structuredParams\":\"{\\\"k\\\":3}\"}";
        MockMultipartFile file = new MockMultipartFile("file", "mirror.json",
                "application/json", json.getBytes(StandardCharsets.UTF_8));

        WaylineEntity exist = wayline(500L, "旧名");
        exist.setFh2WaylineUuid("uuid-fh2-1");
        when(waylineMapper.selectOne(any())).thenReturn(exist);
        when(waylineMapper.selectById(500L)).thenReturn(exist);
        WaylineContentEntity content = new WaylineContentEntity();
        content.setWaylineId(500L);
        when(waylineContentMapper.selectById(500L)).thenReturn(content);

        WaylineDetailVO vo = waylineService.importFile(file, 1L, null);

        assertEquals(500L, vo.getId());
        assertEquals("镜像恢复", vo.getName());
        assertEquals("{\"k\":3}", vo.getContent().getStructuredParams());
        assertEquals(Boolean.TRUE, vo.getRestored());
        verify(waylineMapper, never()).insert(any(WaylineEntity.class));
    }

    @Test
    void import_kmz_shouldUploadToCloudAndRestoreUuidOnEvent() throws Exception {
        // KMZ 导入后同步上传司空，事件返回 wayline_uuid 回写本地 → 可编辑
        byte[] kmz = zipOf("wpmz/template.kml", "<kml><Document><name>云导入</name></Document></kml>");
        MockMultipartFile file = new MockMultipartFile("file", "cloud.kmz",
                "application/vnd.google-earth.kmz", kmz);

        when(kmzStore.save(any(byte[].class))).thenReturn("import/uuid-9.kmz");
        when(kmzStore.pathOf("import/uuid-9.kmz")).thenReturn("/tmp/import/uuid-9.kmz");
        when(waylineMapper.insert(any(WaylineEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, WaylineEntity.class).setId(600L);
            return 1;
        });
        when(waylineContentMapper.insert(any(WaylineContentEntity.class))).thenReturn(1);
        when(waylineContentMapper.selectById(600L)).thenReturn(new WaylineContentEntity());
        when(waylineContentMapper.updateById(any(WaylineContentEntity.class))).thenReturn(1);
        when(fh2SdkClient.available()).thenReturn(true);
        when(fh2SdkClient.importKmzToCloud("/tmp/import/uuid-9.kmz"))
                .thenReturn(new Fh2KmzUploadResult(null, "wayline/import/k9.kmz"));
        AtomicReference<Consumer<Fh2WaylineImportedEvent>> listener = new AtomicReference<>();
        doAnswer(inv -> {
            listener.set(inv.getArgument(0));
            return true;
        }).when(fh2SdkClient).addWaylineImportListener(any());
        waylineService.registerCloudImportListener();

        WaylineDetailVO vo = waylineService.importFile(file, 1L, null);

        assertEquals("云导入", vo.getName());
        // 模拟司空导入完成事件（asyncId 为空，按 kmzObjectKey 关联）：回写 uuid + 同步司空名称
        when(waylineMapper.selectById(600L)).thenReturn(wayline(600L, "云导入"));
        listener.get().accept(new Fh2WaylineImportedEvent(null, "uuid-cloud-9", "wayline/import/k9.kmz", 0, null));
        ArgumentCaptor<WaylineEntity> captor = ArgumentCaptor.forClass(WaylineEntity.class);
        verify(waylineMapper, atLeastOnce()).updateById(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(e ->
                e.getId() == 600L && "uuid-cloud-9".equals(e.getFh2WaylineUuid())));
        verify(fh2SdkClient).updateWaylineName("uuid-cloud-9", "云导入");
    }

    @Test
    void export_mirrorCloudKmzUnreachable_shouldFallbackJson() {
        // 司空镜像：URL 拿到但下载不可达 → 降级封装 JSON（不出错）
        WaylineEntity e = wayline(1L, "镜像");
        e.setFh2WaylineUuid("uuid-m1");
        when(waylineMapper.selectById(1L)).thenReturn(e);
        when(waylineContentMapper.selectById(1L)).thenReturn(null);
        when(fh2SdkClient.available()).thenReturn(true);
        when(fh2SdkClient.fetchWaylineKmzUrl("uuid-m1")).thenReturn("http://127.0.0.1:1/x.kmz");

        WaylineExportFile file = waylineService.export(1L);

        assertEquals(WaylineExportFile.TYPE_JSON, file.contentType());
        assertTrue(file.filename().endsWith(".json"));
    }

    @Test
    void matchCloudUuidByKmzName_shouldSkipUuidsAlreadyTakenLocally() {
        // 司空侧同名两条（原航线 + 导入副本，名字都是 kmz 文件名）：本地已占用原航线 uuid，只应匹配到未占用的新 uuid
        WaylineEntity old = wayline(25L, "简单航线");
        old.setFh2WaylineUuid("uuid-old");
        when(waylineMapper.selectList(any())).thenReturn(List.of(old));
        when(fh2SdkClient.listWaylines(0, 100)).thenReturn(List.of(
                Fh2WaylineItemVO.builder().waylineName("简单航线").waylineUuid("uuid-old").build(),
                Fh2WaylineItemVO.builder().waylineName("简单航线").waylineUuid("uuid-new").build(),
                Fh2WaylineItemVO.builder().waylineName("其他航线").waylineUuid("uuid-x").build()));

        assertEquals("uuid-new", waylineService.matchCloudUuidByKmzName("简单航线"));
    }

    @Test
    void export_kmzWithActionUuid_shouldStripToBreakMediaLink() throws Exception {
        // 原航线 KMZ 带 actionUUID（司空在线任务录制的媒体回联键）：导出必须删除，
        // 否则司空导入副本时会按该键建空 action-media 骨架，编辑器弱比较误报"航点位置已变更"
        when(waylineMapper.selectById(1L)).thenReturn(wayline(1L, "带媒体航线"));
        WaylineContentEntity content = new WaylineContentEntity();
        content.setStorageKey("import/uuid-media.kmz");
        when(waylineContentMapper.selectById(1L)).thenReturn(content);
        String oldUuid = "159f6908-42b6-4c5f-8a56-76212530cb81";
        byte[] kmz = zipOfEntries(
                "wpmz/template.kml", "<kml><wpml:actionUUID>" + oldUuid + "</wpml:actionUUID></kml>",
                "wpmz/waylines.wpml", "<wpml:doc><wpml:actionUUID>" + oldUuid + "</wpml:actionUUID></wpml:doc>");
        when(kmzStore.open("import/uuid-media.kmz")).thenReturn(kmz);

        WaylineExportFile file = waylineService.export(1L);

        assertEquals(WaylineExportFile.TYPE_KMZ, file.contentType());
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(file.data()))) {
            ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                if (ze.getName().endsWith(".kml") || ze.getName().endsWith(".wpml")) {
                    String text = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                    assertFalse(text.contains("<wpml:actionUUID>"), ze.getName() + " must not contain actionUUID");
                }
            }
        }
    }

    private WaylineEntity wayline(Long id, String name) {
        WaylineEntity e = new WaylineEntity();
        e.setId(id);
        e.setWorkspaceId(1L);
        e.setFolderId(1L);
        e.setName(name);
        e.setAircraftModel("M350 RTK");
        e.setWaylineType("waypoint");
        e.setStatus("draft");
        return e;
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

    private static byte[] zipOfEntries(String name1, String content1, String name2, String content2) {
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(buf)) {
            zos.putNextEntry(new ZipEntry(name1));
            zos.write(content1.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(name2));
            zos.write(content2.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            return buf.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
