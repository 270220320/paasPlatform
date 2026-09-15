package com.uavplatform.wayline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.PermissionChecker;
import com.uavplatform.common.security.UserContext;
import com.uavplatform.fh2.dto.Fh2KmzUploadResult;
import com.uavplatform.fh2.dto.Fh2WaylineImportedEvent;
import com.uavplatform.fh2.dto.Fh2WaylineItemVO;
import com.uavplatform.fh2.sdk.Fh2SdkClient;
import com.uavplatform.mission.domain.MissionPlanEntity;
import com.uavplatform.mission.mapper.MissionPlanMapper;
import com.uavplatform.notification.service.NotificationService;
import com.uavplatform.wayline.domain.WaylineContentEntity;
import com.uavplatform.wayline.domain.WaylineEntity;
import com.uavplatform.wayline.domain.WaylineFolderEntity;
import com.uavplatform.wayline.domain.WaylineSnapshotEntity;
import com.uavplatform.wayline.dto.WaylineContentVO;
import com.uavplatform.wayline.dto.WaylineCreateRequest;
import com.uavplatform.wayline.dto.WaylineDetailVO;
import com.uavplatform.wayline.dto.WaylineExportFile;
import com.uavplatform.wayline.dto.WaylineFolderCreateRequest;
import com.uavplatform.wayline.dto.WaylineFolderVO;
import com.uavplatform.wayline.dto.WaylineListVO;
import com.uavplatform.wayline.dto.WaylineUpdateRequest;
import com.uavplatform.wayline.mapper.WaylineContentMapper;
import com.uavplatform.wayline.mapper.WaylineFolderMapper;
import com.uavplatform.wayline.mapper.WaylineMapper;
import com.uavplatform.wayline.mapper.WaylineSnapshotMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaylineService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_FOLDER_NAME = "默认文件夹";
    private static final Set<String> ALLOWED_TYPES = Set.of("waypoint", "grid_2d");
    private static final Set<String> BLOCKING_MISSION_STATUS = Set.of("pending", "running", "paused");
    /** 导入未指定机型时的兑底（保持历史默认） */
    private static final String DEFAULT_IMPORT_AIRCRAFT_MODEL = "M350 RTK";
    /** 司空"在线任务录制"媒体回联键（template.kml 与 waylines.wpml 成对出现） */
    private static final Pattern ACTION_UUID_PATTERN =
            Pattern.compile("<wpml:actionUUID>[^<]*</wpml:actionUUID>");

    private final WaylineMapper waylineMapper;
    private final WaylineContentMapper waylineContentMapper;
    private final WaylineFolderMapper waylineFolderMapper;
    private final WaylineSnapshotMapper waylineSnapshotMapper;
    private final MissionPlanMapper missionPlanMapper;
    private final Fh2SdkClient fh2SdkClient;
    private final WaylineKmzStore kmzStore;
    private final NotificationService notificationService;

    /** importKmz 异步请求标识/对象 key → 本地航线 id；事件到达后回写 fh2_wayline_uuid（服务重启丢失仅影响回写，不影响本地记录） */
    private final ConcurrentHashMap<String, Long> pendingCloudImports = new ConcurrentHashMap<>();
    /** 司空 KMZ 下载专用（私有化内部地址，可能自签证书故跳过校验） */
    private volatile HttpClient kmzDownloader;
    /** 事件不可达时的 uuid 回写兜底：延迟轮询司空列表按 kmz 文件名匹配 */
    private final ScheduledExecutorService cloudImportResolver = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "wayline-cloud-import-resolver");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    void registerCloudImportListener() {
        fh2SdkClient.addWaylineImportListener(this::onCloudWaylineImported);
    }

    public List<WaylineFolderVO> listFolders() {
        PermissionChecker.require("wayline:read");
        // 一次查出各文件夹航线数，避免逐文件夹 count 的 N+1 查询
        Map<Long, Long> waylineCounts = waylineMapper.selectList(new LambdaQueryWrapper<WaylineEntity>()
                        .eq(WaylineEntity::getWorkspaceId, workspaceId())
                        .select(WaylineEntity::getFolderId))
                .stream()
                .filter(w -> w.getFolderId() != null)
                .collect(Collectors.groupingBy(WaylineEntity::getFolderId, Collectors.counting()));
        return waylineFolderMapper.selectList(new LambdaQueryWrapper<WaylineFolderEntity>()
                        .eq(WaylineFolderEntity::getWorkspaceId, workspaceId())
                        .orderByAsc(WaylineFolderEntity::getName))
                .stream().map(f -> toFolderVO(f, waylineCounts.getOrDefault(f.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public IdVO createFolder(WaylineFolderCreateRequest request) {
        PermissionChecker.require("wayline:write");
        WaylineFolderEntity folder = new WaylineFolderEntity();
        folder.setWorkspaceId(workspaceId());
        folder.setName(request.getName().trim());
        folder.setParentId(request.getParentId());
        waylineFolderMapper.insert(folder);
        return new IdVO(folder.getId());
    }

    /**
     * 删除文件夹（含最后一个）：有航线的文件夹不可删（需先删除或移出航线）；
     * 删除后不自动重建任何文件夹，子文件夹挂回根级。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFolder(Long id) {
        PermissionChecker.require("wayline:write");
        WaylineFolderEntity folder = waylineFolderMapper.selectById(id);
        if (folder == null || !workspaceId().equals(folder.getWorkspaceId())) {
            throw new BizException(ErrorCodes.WAYLINE_FOLDER_NOT_FOUND, "文件夹不存在");
        }
        Long waylineCount = waylineMapper.selectCount(new LambdaQueryWrapper<WaylineEntity>()
                .eq(WaylineEntity::getFolderId, id)
                .eq(WaylineEntity::getWorkspaceId, workspaceId()));
        if (waylineCount != null && waylineCount > 0) {
            throw new BizException(ErrorCodes.WAYLINE_FOLDER_NOT_EMPTY,
                    "文件夹下有 " + waylineCount + " 条航线，请先删除或移出后再删除文件夹");
        }
        // 子文件夹挂回根级，避免悬空引用（set(null) 才能真正置空，entity patch 会跳过 null 字段）
        waylineFolderMapper.update(null, new LambdaUpdateWrapper<WaylineFolderEntity>()
                .set(WaylineFolderEntity::getParentId, null)
                .eq(WaylineFolderEntity::getParentId, id)
                .eq(WaylineFolderEntity::getWorkspaceId, workspaceId()));
        waylineFolderMapper.deleteById(id);
    }

    /**
     * 航线列表读本地库（按文件夹 / 名称 / 状态过滤），未删除记录；司空侧由编辑器 paas.js 保存。
     */
    public PageResult<WaylineListVO> page(Long folderId, String aircraftModel, String status,
                                          String keyword, long page, long pageSize) {
        PermissionChecker.require("wayline:read");
        long p = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        LambdaQueryWrapper<WaylineEntity> qw = new LambdaQueryWrapper<WaylineEntity>()
                .eq(WaylineEntity::getWorkspaceId, workspaceId());
        if (folderId != null) {
            qw.eq(WaylineEntity::getFolderId, folderId);
        }
        if (StringUtils.hasText(aircraftModel)) {
            qw.eq(WaylineEntity::getAircraftModel, aircraftModel.trim());
        }
        if (StringUtils.hasText(status)) {
            qw.eq(WaylineEntity::getStatus, status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            qw.like(WaylineEntity::getName, keyword.trim());
        }
        qw.orderByDesc(WaylineEntity::getUpdatedAt);
        Page<WaylineEntity> result = waylineMapper.selectPage(new Page<>(p, size), qw);
        List<WaylineListVO> list = result.getRecords().stream().map(this::toListVO).collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), p, size);
    }

    /** 仅在创建航线等必须归属时兜底：取默认文件夹，无则取最早文件夹，都没有则新建默认文件夹。 */
    @Transactional(rollbackFor = Exception.class)
    public Long ensureDefaultFolder() {
        Long ws = workspaceId();
        WaylineFolderEntity exist = waylineFolderMapper.selectOne(new LambdaQueryWrapper<WaylineFolderEntity>()
                .eq(WaylineFolderEntity::getWorkspaceId, ws)
                .eq(WaylineFolderEntity::getName, DEFAULT_FOLDER_NAME)
                .last("LIMIT 1"));
        Long folderId;
        if (exist != null) {
            folderId = exist.getId();
        } else {
            Long count = waylineFolderMapper.selectCount(new LambdaQueryWrapper<WaylineFolderEntity>()
                    .eq(WaylineFolderEntity::getWorkspaceId, ws));
            if (count != null && count > 0) {
                WaylineFolderEntity first = waylineFolderMapper.selectOne(new LambdaQueryWrapper<WaylineFolderEntity>()
                        .eq(WaylineFolderEntity::getWorkspaceId, ws)
                        .orderByAsc(WaylineFolderEntity::getId)
                        .last("LIMIT 1"));
                folderId = first == null ? null : first.getId();
            } else {
                WaylineFolderEntity folder = new WaylineFolderEntity();
                folder.setWorkspaceId(ws);
                folder.setName(DEFAULT_FOLDER_NAME);
                waylineFolderMapper.insert(folder);
                folderId = folder.getId();
            }
        }
        if (folderId != null) {
            WaylineEntity patch = new WaylineEntity();
            patch.setFolderId(folderId);
            waylineMapper.update(patch, new LambdaQueryWrapper<WaylineEntity>()
                    .eq(WaylineEntity::getWorkspaceId, ws)
                    .isNull(WaylineEntity::getFolderId));
        }
        return folderId;
    }

    public WaylineDetailVO detail(Long id) {
        PermissionChecker.require("wayline:read");
        WaylineEntity entity = requireWayline(id);
        WaylineContentEntity content = waylineContentMapper.selectById(id);
        return toDetailVO(entity, content);
    }

    @Transactional(rollbackFor = Exception.class)
    public WaylineDetailVO create(WaylineCreateRequest request) {
        PermissionChecker.require("wayline:write");
        validateName(request.getName());
        validateType(request.getWaylineType());

        WaylineEntity entity = new WaylineEntity();
        entity.setWorkspaceId(workspaceId());
        entity.setFolderId(request.getFolderId() != null ? request.getFolderId() : ensureDefaultFolder());
        entity.setName(request.getName().trim());
        entity.setAircraftModel(request.getAircraftModel().trim());
        entity.setWaylineType(request.getWaylineType());
        entity.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "draft");
        entity.setUpdatedBy(currentUserId());
        waylineMapper.insert(entity);

        WaylineContentEntity content = new WaylineContentEntity();
        content.setWaylineId(entity.getId());
        content.setStructuredParams(request.getStructuredParams());
        content.setChecksum(computeChecksum(request.getStructuredParams()));
        waylineContentMapper.insert(content);
        return toDetailVO(entity, content);
    }

    @Transactional(rollbackFor = Exception.class)
    public WaylineDetailVO update(Long id, WaylineUpdateRequest request) {
        PermissionChecker.require("wayline:write");
        WaylineEntity entity = requireWayline(id);
        if (StringUtils.hasText(request.getName())) {
            validateName(request.getName());
            entity.setName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getStatus())) {
            entity.setStatus(request.getStatus());
        }
        if (request.getFolderId() != null) {
            entity.setFolderId(request.getFolderId());
        }
        entity.setUpdatedBy(currentUserId());
        waylineMapper.updateById(entity);

        WaylineContentEntity content = waylineContentMapper.selectById(id);
        if (content == null) {
            content = new WaylineContentEntity();
            content.setWaylineId(id);
        }
        if (request.getStructuredParams() != null) {
            content.setStructuredParams(request.getStructuredParams());
            content.setChecksum(computeChecksum(request.getStructuredParams()));
        }
        if (waylineContentMapper.selectById(id) == null) {
            waylineContentMapper.insert(content);
        } else {
            waylineContentMapper.updateById(content);
        }
        return toDetailVO(entity, content);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PermissionChecker.require("wayline:write");
        WaylineEntity entity = waylineMapper.selectById(id);
        if (entity != null && workspaceId().equals(entity.getWorkspaceId())
                && StringUtils.hasText(entity.getFh2WaylineUuid())) {
            deleteRemoteAndLocal(entity.getFh2WaylineUuid(), entity);
            return;
        }
        if (entity == null || !workspaceId().equals(entity.getWorkspaceId())) {
            throw new BizException(ErrorCodes.WAYLINE_NOT_FOUND, "航线不存在");
        }
        Long inUse = missionPlanMapper.selectCount(new LambdaQueryWrapper<MissionPlanEntity>()
                .eq(MissionPlanEntity::getWaylineId, id)
                .in(MissionPlanEntity::getStatus, BLOCKING_MISSION_STATUS));
        if (inUse != null && inUse > 0) {
            throw new BizException(ErrorCodes.WAYLINE_IN_USE, "航线被任务引用，禁止删除");
        }
        cleanupKmzFile(id);
        waylineContentMapper.deleteById(id);
        waylineMapper.deleteById(id);
    }

    /** 删除航线时同步清理本地 KMZ 本体，避免孤儿文件 */
    private void cleanupKmzFile(Long waylineId) {
        WaylineContentEntity content = waylineContentMapper.selectById(waylineId);
        if (content != null) {
            kmzStore.delete(content.getStorageKey());
        }
    }

    /** 按司空航线 UUID 删除远端，并清理本地镜像（如有）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUuid(String waylineUuid) {
        PermissionChecker.require("wayline:write");
        if (!StringUtils.hasText(waylineUuid)) {
            throw new BizException(ErrorCodes.BAD_REQUEST, "waylineUuid 必填");
        }
        WaylineEntity local = waylineMapper.selectOne(new LambdaQueryWrapper<WaylineEntity>()
                .eq(WaylineEntity::getFh2WaylineUuid, waylineUuid.trim())
                .last("LIMIT 1"));
        deleteRemoteAndLocal(waylineUuid.trim(), local);
    }

    private void deleteRemoteAndLocal(String waylineUuid, WaylineEntity local) {
        if (local != null) {
            Long inUse = missionPlanMapper.selectCount(new LambdaQueryWrapper<MissionPlanEntity>()
                    .eq(MissionPlanEntity::getWaylineId, local.getId())
                    .in(MissionPlanEntity::getStatus, BLOCKING_MISSION_STATUS));
            if (inUse != null && inUse > 0) {
                throw new BizException(ErrorCodes.WAYLINE_IN_USE, "航线被任务引用，禁止删除");
            }
        }
        // SDK 未就绪（如私有化未配置 userToken/bindingUuid）：当前部署不连司空，降级为仅删本地镜像
        if (!fh2SdkClient.available()) {
            if (local == null) {
                throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE,
                        "无法连接航线服务，且本地无该航线镜像：" + waylineUuid);
            }
            log.warn("[wayline] FH2 SDK 未就绪，仅删除本地镜像 uuid={}", waylineUuid);
            deleteLocalMirror(local);
            return;
        }
        try {
            fh2SdkClient.deleteWaylines(List.of(waylineUuid));
        } catch (Exception e) {
            log.error("[wayline] FH2 delete failed uuid={}: {}", waylineUuid, e.getMessage(), e);
            // 远端航线已不存在（旧环境同步过来的残留数据）时放行本地清理
            if (local != null && isRemoteWaylineMissing(e)) {
                log.warn("[wayline] 远端航线不存在，清理本地镜像 uuid={}", waylineUuid);
                deleteLocalMirror(local);
                return;
            }
            throw new BizException(ErrorCodes.FH2_SDK_UNAVAILABLE, "删除航线失败：" + e.getMessage());
        }
        if (local != null) {
            deleteLocalMirror(local);
        }
    }

    private void deleteLocalMirror(WaylineEntity local) {
        cleanupKmzFile(local.getId());
        waylineContentMapper.deleteById(local.getId());
        waylineMapper.deleteById(local.getId());
    }

    /** 宽松识别司空侧“航线不存在”类错误，避免残留镜像无法删除 */
    private boolean isRemoteWaylineMissing(Exception e) {
        String msg = String.valueOf(e.getMessage()).toLowerCase();
        return msg.contains("not exist") || msg.contains("not found") || msg.contains("不存在");
    }

    /**
     * 导入（U-032）双通道：zip 魔数走 KMZ 链（结构校验 40003 → 元数据提取 → 本体落盘，
     * checksum=KMZ 字节 SHA-256，structuredParams 存导入摘要）；否则按导出封装 JSON
     * 恢复（structuredParams 原样回填，无 KMZ 本体），实现导出→再导入闭环。
     */
    @Transactional(rollbackFor = Exception.class)
    public WaylineDetailVO importFile(MultipartFile file, Long folderId, String aircraftModel) {
        PermissionChecker.require("wayline:write");
        byte[] data = readImportFile(file);
        String filename = file.getOriginalFilename();
        String fallbackName = stripSuffix(filename);

        WaylineCreateRequest req = new WaylineCreateRequest();
        req.setFolderId(folderId);
        req.setStatus("draft");

        if (isZip(data)) {
            WaylineKmzParser.ParsedKmz parsed = WaylineKmzParser.parse(data, fallbackName);
            String storageKey = kmzStore.save(data);
            req.setName(truncateName(parsed.name() != null ? parsed.name() : fallbackName));
            req.setAircraftModel(StringUtils.hasText(aircraftModel) ? aircraftModel.trim() : DEFAULT_IMPORT_AIRCRAFT_MODEL);
            req.setWaylineType("waypoint");
            req.setStructuredParams(buildImportSummary(filename, data.length, parsed.waypointCount()));

            WaylineDetailVO created = create(req);
            WaylineContentEntity content = waylineContentMapper.selectById(created.getId());
            content.setStorageKey(storageKey);
            content.setChecksum(sha256Hex(data));
            waylineContentMapper.updateById(content);
            created.getContent().setStorageKey(storageKey);
            created.getContent().setChecksum(content.getChecksum());
            // 同步上传司空：成功后事件回写 uuid，本地导入的航线即可在编辑器打开（失败保持纯本地）
            tryUploadToCloud(created.getId(), storageKey);
            return created;
        }

        WaylineExportCodec.Decoded decoded = WaylineExportCodec.decode(data, fallbackName);
        req.setName(truncateName(decoded.name()));
        req.setAircraftModel(firstNonBlank(aircraftModel, decoded.aircraftModel(), DEFAULT_IMPORT_AIRCRAFT_MODEL));
        // 导出文件类型不在当前白名单时宽松回退 waypoint，structuredParams 原样保留
        req.setWaylineType(decoded.waylineType() != null && ALLOWED_TYPES.contains(decoded.waylineType())
                ? decoded.waylineType() : "waypoint");
        req.setStructuredParams(decoded.structuredParams());
        if ("draft".equals(decoded.status()) || "published".equals(decoded.status())) {
            req.setStatus(decoded.status());
        }

        // 司空镜像备份恢复：回写 fh2WaylineUuid 使恢复后的航线可在编辑器打开；
        // 同 uuid 已存在（备份的原记录还在）则恢复更新原记录，避免同一司空航线双份镜像
        String fh2Uuid = StringUtils.hasText(decoded.fh2WaylineUuid()) ? decoded.fh2WaylineUuid().trim() : null;
        if (fh2Uuid != null) {
            WaylineEntity exist = waylineMapper.selectOne(new LambdaQueryWrapper<WaylineEntity>()
                    .eq(WaylineEntity::getFh2WaylineUuid, fh2Uuid)
                    .eq(WaylineEntity::getWorkspaceId, workspaceId())
                    .last("LIMIT 1"));
            if (exist != null) {
                WaylineUpdateRequest restore = new WaylineUpdateRequest();
                restore.setName(req.getName());
                restore.setStatus(req.getStatus());
                restore.setStructuredParams(req.getStructuredParams());
                WaylineDetailVO vo = update(exist.getId(), restore);
                vo.setRestored(true);
                return vo;
            }
        }
        WaylineDetailVO created = create(req);
        if (fh2Uuid != null) {
            WaylineEntity patch = new WaylineEntity();
            patch.setId(created.getId());
            patch.setFh2WaylineUuid(fh2Uuid);
            waylineMapper.updateById(patch);
        }
        return created;
    }

    /**
     * 导出（U-032）：本地 KMZ 本体优先（application/vnd.google-earth.kmz，可再导入）；
     * 无本体时导出 uav-wayline-export 封装 JSON（含元数据+structuredParams，同样可再导入），
     * 保证司空镜像航线也能备份迁移。
     */
    public WaylineExportFile export(Long id) {
        PermissionChecker.require("wayline:read");
        WaylineEntity entity = requireWayline(id);
        WaylineContentEntity content = waylineContentMapper.selectById(id);
        String baseName = sanitizeFilename(entity.getName(), "wayline-" + id);

        if (content != null) {
            byte[] kmz = kmzStore.open(content.getStorageKey());
            if (kmz != null) {
                return new WaylineExportFile(stripActionUuids(kmz), baseName + ".kmz", WaylineExportFile.TYPE_KMZ);
            }
        }
        // 司空镜像：getWaylineDetail 拿临时下载 URL 直接下载 KMZ（导出即 .kmz，可再导入）
        if (StringUtils.hasText(entity.getFh2WaylineUuid()) && fh2SdkClient.available()) {
            try {
                String url = fh2SdkClient.fetchWaylineKmzUrl(entity.getFh2WaylineUuid());
                byte[] kmz = StringUtils.hasText(url) ? downloadKmz(url) : null;
                if (kmz != null) {
                    return new WaylineExportFile(stripActionUuids(kmz), baseName + ".kmz", WaylineExportFile.TYPE_KMZ);
                }
            } catch (Exception e) {
                log.warn("[wayline] 司空 KMZ 拉取失败 id={} uuid={}: {}，降级封装 JSON",
                        id, entity.getFh2WaylineUuid(), e.getMessage());
            }
        }
        return new WaylineExportFile(WaylineExportCodec.encode(entity, content).getBytes(StandardCharsets.UTF_8),
                baseName + ".json", WaylineExportFile.TYPE_JSON);
    }

    private byte[] readImportFile(MultipartFile file) {
        if (file == null || file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new BizException(ErrorCodes.WAYLINE_PARSE_FAIL, "导入失败：文件为空或缺少文件名");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCodes.WAYLINE_PARSE_FAIL, "导入失败：文件读取失败");
        }
    }

    /** zip 魔数 PK\x03\x04（兼容空档/跨卷 PK\x05\x06、PK\x07\x08） */
    private boolean isZip(byte[] data) {
        return data.length > 3 && data[0] == 'P' && data[1] == 'K'
                && (data[2] == 3 || data[2] == 5 || data[2] == 7);
    }

    private String stripSuffix(String filename) {
        String n = filename == null ? null : filename.trim();
        if (!StringUtils.hasText(n)) {
            return null;
        }
        String lower = n.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".kmz")) {
            return n.substring(0, n.length() - 4);
        }
        if (lower.endsWith(".json")) {
            return n.substring(0, n.length() - 5);
        }
        return n;
    }

    private String firstNonBlank(String... candidates) {
        for (String s : candidates) {
            if (StringUtils.hasText(s)) {
                return s.trim();
            }
        }
        return null;
    }

    /** 航线名截断到 60 字（validateName 上限）；名称来源为空时返回 null 由 create 校验拦截 */
    private String truncateName(String name) {
        String n = name == null ? null : name.trim();
        if (n == null || n.isEmpty() || n.length() <= 60) {
            return n;
        }
        return n.substring(0, 60);
    }

    private String buildImportSummary(String filename, long sizeBytes, Integer waypointCount) {
        return "{\"imported\":true"
                + ",\"sourceFilename\":\"" + jsonEscape(filename) + "\""
                + ",\"fileSizeBytes\":" + sizeBytes
                + (waypointCount != null ? ",\"waypointCount\":" + waypointCount : "")
                + "}";
    }

    /** Windows/Linux 文件名非法字符归一为下划线；空白名回退 fallback */
    private String sanitizeFilename(String name, String fallback) {
        String cleaned = name == null ? "" : name.trim().replaceAll("[\\\\/:*?\\\"<>|\\s]+", "_");
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception ex) {
            return null;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public WaylineSnapshotEntity createSnapshot(WaylineEntity wayline, WaylineContentEntity content) {
        WaylineSnapshotEntity snapshot = new WaylineSnapshotEntity();
        snapshot.setSourceWaylineId(wayline.getId());
        snapshot.setName(StringUtils.hasText(wayline.getName()) ? wayline.getName() : "未命名航线");
        snapshot.setAircraftModel(StringUtils.hasText(wayline.getAircraftModel()) ? wayline.getAircraftModel() : "unknown");
        snapshot.setWaylineType(StringUtils.hasText(wayline.getWaylineType()) ? wayline.getWaylineType() : "waypoint");
        if (content != null) {
            snapshot.setStorageKey(content.getStorageKey());
            snapshot.setStructuredParams(content.getStructuredParams());
            snapshot.setChecksum(content.getChecksum());
        }
        waylineSnapshotMapper.insert(snapshot);
        return snapshot;
    }

    public WaylineEntity requirePublishedWayline(Long id) {
        WaylineEntity entity = requireWayline(id);
        // 本地镜像可能尚未标 published，有司空 UUID 的航线也允许建计划
        if (!"published".equals(entity.getStatus()) && !StringUtils.hasText(entity.getFh2WaylineUuid())) {
            throw new BizException(ErrorCodes.WAYLINE_UNAVAILABLE, "航线不可用");
        }
        return entity;
    }

    public WaylineEntity requireWayline(Long id) {
        WaylineEntity entity = waylineMapper.selectById(id);
        if (entity == null || !workspaceId().equals(entity.getWorkspaceId())) {
            throw new BizException(ErrorCodes.WAYLINE_NOT_FOUND, "航线不存在");
        }
        return entity;
    }

    private void validateName(String name) {
        if (!StringUtils.hasText(name) || name.trim().length() > 60) {
            throw new BizException(ErrorCodes.WAYLINE_NAME_INVALID, "航线名称非法");
        }
    }

    private void validateType(String type) {
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BizException(ErrorCodes.WAYLINE_TYPE_UNSUPPORTED, "不支持的航线类型");
        }
    }

    private String computeChecksum(String structuredParams) {
        if (!StringUtils.hasText(structuredParams)) {
            return null;
        }
        return sha256Hex(structuredParams.getBytes(StandardCharsets.UTF_8));
    }

    private WaylineFolderVO toFolderVO(WaylineFolderEntity f, long waylineCount) {
        return WaylineFolderVO.builder()
                .id(f.getId())
                .name(f.getName())
                .parentId(f.getParentId())
                .createdAt(f.getCreatedAt() == null ? null : f.getCreatedAt().format(FMT))
                .waylineCount(waylineCount)
                .build();
    }

    /** 上传 KMZ 到司空（importKmz）；失败仅记日志，不影响本地导入结果 */
    private void tryUploadToCloud(Long waylineId, String storageKey) {
        if (!fh2SdkClient.available()) {
            return;
        }
        String path = kmzStore.pathOf(storageKey);
        if (!StringUtils.hasText(path)) {
            log.warn("[wayline] importKmz 跳过：本地 KMZ 文件缺失 waylineId={}", waylineId);
            return;
        }
        try {
            Fh2KmzUploadResult upload = fh2SdkClient.importKmzToCloud(path);
            String pendingKey = upload != null ? upload.pendingKey() : null;
            if (pendingKey != null) {
                pendingCloudImports.put(pendingKey, waylineId);
                log.info("[wayline] importKmz 已提交司空 waylineId={} asyncId={} kmzKey={}",
                        waylineId, upload.asyncRequestId(), upload.kmzObjectKey());
                // 事件不可达时兜底：司空侧航线名 = kmz 文件名去后缀，延迟轮询列表匹配回写
                scheduleUuidResolve(waylineId, kmzBaseName(upload.kmzObjectKey()), 3);
            } else {
                log.warn("[wayline] importKmz 未返回关联键 waylineId={}，保持纯本地导入", waylineId);
            }
        } catch (Exception e) {
            log.warn("[wayline] importKmz 提交失败 waylineId={}: {}，保持纯本地导入", waylineId, e.getMessage());
        }
    }

    /** kmzObjectKey 形如 wayline/import/{uuid}.kmz → 取文件名去后缀（司空侧航线名） */
    private static String kmzBaseName(String kmzObjectKey) {
        if (!StringUtils.hasText(kmzObjectKey)) {
            return null;
        }
        String name = kmzObjectKey;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.endsWith(".kmz") ? name.substring(0, name.length() - 4) : name;
    }

    /** 轮询司空列表按 kmz 文件名（司空侧航线名）匹配 uuid；attempts 递减重试 */
    private void scheduleUuidResolve(Long waylineId, String kmzBaseName, int attempts) {
        if (kmzBaseName == null || attempts <= 0) {
            return;
        }
        cloudImportResolver.schedule(() -> {
            try {
                if (cloudUuidAlreadySet(waylineId)) {
                    return; // 事件先到已回写
                }
                String uuid = matchCloudUuidByKmzName(kmzBaseName);
                if (uuid != null) {
                    writeCloudUuid(waylineId, uuid, "poll");
                } else {
                    scheduleUuidResolve(waylineId, kmzBaseName, attempts - 1);
                }
            } catch (Exception e) {
                log.warn("[wayline] 司空 uuid 轮询失败 waylineId={}: {}", waylineId, e.getMessage());
                scheduleUuidResolve(waylineId, kmzBaseName, attempts - 1);
            }
        }, 6, TimeUnit.SECONDS);
    }

    private boolean cloudUuidAlreadySet(Long waylineId) {
        WaylineEntity local = waylineMapper.selectById(waylineId);
        return local == null || StringUtils.hasText(local.getFh2WaylineUuid());
    }

    String matchCloudUuidByKmzName(String kmzBaseName) {
        try {
            // 排除本地已占用的 uuid：导出→导入的副本与原航线在司空侧同名并存，避免误关联到旧航线
            Set<String> taken = new HashSet<>();
            for (WaylineEntity e : waylineMapper.selectList(new LambdaQueryWrapper<WaylineEntity>()
                    .isNotNull(WaylineEntity::getFh2WaylineUuid))) {
                if (StringUtils.hasText(e.getFh2WaylineUuid())) taken.add(e.getFh2WaylineUuid());
            }
            for (Fh2WaylineItemVO item : fh2SdkClient.listWaylines(0, 100)) {
                if (item != null && kmzBaseName.equals(item.getWaylineName())
                        && StringUtils.hasText(item.getWaylineUuid())
                        && !taken.contains(item.getWaylineUuid())) {
                    return item.getWaylineUuid();
                }
            }
        } catch (Exception e) {
            log.warn("[wayline] 司空航线列表拉取失败: {}", e.getMessage());
        }
        return null;
    }

    /** 回写 uuid + 把司空侧 uuid 命名同步为本地航线名；source 场景：event/poll */
    private void writeCloudUuid(Long waylineId, String uuid, String via) {
        WaylineEntity patch = new WaylineEntity();
        patch.setId(waylineId);
        patch.setFh2WaylineUuid(uuid.trim());
        waylineMapper.updateById(patch);
        log.info("[wayline] 司空导入完成({}) waylineId={} uuid={}，已可编辑", via, waylineId, uuid);
        // 司空侧默认用 uuid 命名，同步为本地航线名，便于在司空控制台识别
        WaylineEntity local = waylineMapper.selectById(waylineId);
        if (local != null && StringUtils.hasText(local.getName())) {
            try {
                fh2SdkClient.updateWaylineName(uuid.trim(), local.getName());
            } catch (Exception e) {
                log.warn("[wayline] 司空航线重命名失败 uuid={}: {}", uuid, e.getMessage());
            }
        }
    }

    /** 司空 importKmz 完成事件：回写 wayline_uuid 使本地导入航线可编辑，并同步本地名称；同时发站内通知 */
    private void onCloudWaylineImported(Fh2WaylineImportedEvent event) {
        // 司空可能只回填 asyncRequestId 或 kmzObjectKey 之一，按同样规则生成关联键
        Fh2KmzUploadResult probe = new Fh2KmzUploadResult(event.asyncRequestId(), event.kmzObjectKey());
        String key = probe.pendingKey();
        Long waylineId = key != null ? pendingCloudImports.remove(key) : null;
        if (waylineId == null) {
            return;
        }
        if (event.code() != 0 || !StringUtils.hasText(event.waylineUuid())) {
            log.warn("[wayline] 司空导入 KMZ 失败 waylineId={} code={} msg={}", waylineId, event.code(), event.message());
            notifyWaylineImport(waylineId, false,
                    "code=" + event.code() + (StringUtils.hasText(event.message()) ? " " + event.message() : ""));
            return;
        }
        writeCloudUuid(waylineId, event.waylineUuid(), "event");
        notifyWaylineImport(waylineId, true, null);
    }

    /** 航线导入结果通知；发布失败不影响回写主流程 */
    private void notifyWaylineImport(Long waylineId, boolean success, String error) {
        try {
            WaylineEntity wayline = waylineMapper.selectById(waylineId);
            if (wayline == null) {
                return;
            }
            String name = StringUtils.hasText(wayline.getName()) ? wayline.getName() : ("航线 #" + waylineId);
            if (success) {
                notificationService.publish(wayline.getWorkspaceId(), "wayline.imported", "success",
                        "航线导入完成：" + name,
                        "航线「" + name + "」已在司空解析完成，可编辑与下发。",
                        "wayline", waylineId);
            } else {
                notificationService.publish(wayline.getWorkspaceId(), "wayline.import_failed", "error",
                        "航线导入失败：" + name,
                        "司空解析 KMZ 失败（" + error + "），请检查文件后重新导入。",
                        "wayline", waylineId);
            }
        } catch (Exception e) {
            log.warn("[wayline] notify import waylineId={} failed: {}", waylineId, e.getMessage());
        }
    }

    /** 下载司空 KMZ（临时 URL，含重定向）；失败返回 null 走 JSON 降级 */
    private byte[] downloadKmz(String url) {
        try {
            if (kmzDownloader == null) {
                kmzDownloader = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .sslContext(trustAllContext())
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
            }
            HttpResponse<byte[]> resp = kmzDownloader.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                return resp.body();
            }
            log.warn("[wayline] 司空 KMZ 下载异常 status={} url={}", resp.statusCode(), url);
        } catch (Exception e) {
            log.warn("[wayline] 司空 KMZ 下载失败 url={}: {}", url, e.getMessage());
        }
        return null;
    }

    /**
     * 导出 KMZ 时删除 wpml 里的 actionUUID（司空"在线任务录制"媒体回联键）：
     * 司空导入 KMZ 时会按该键为拍照动作创建空的 action-media 骨架，编辑器打开副本时对骨架的
     * 空对象弱比较（create_at=undefined!==−1 误判已归档、shoot_position={} 比对 NaN）会误报
     * "航点位置已变更"。删除后司空不再建骨架（已实测 action-media 返回空），编辑器无从匹配，
     * 误报消除；原航线的既有媒体归档不受影响。无 actionUUID 或处理失败时按原样导出。
     */
    private byte[] stripActionUuids(byte[] kmz) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean stripped = false;
            try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(kmz));
                 ZipOutputStream zout = new ZipOutputStream(out)) {
                ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zout.putNextEntry(new ZipEntry(entry.getName()));
                        continue;
                    }
                    byte[] data = zin.readAllBytes();
                    String name = entry.getName();
                    if (name.endsWith(".kml") || name.endsWith(".wpml")) {
                        String text = new String(data, StandardCharsets.UTF_8);
                        Matcher m = ACTION_UUID_PATTERN.matcher(text);
                        if (m.find()) {
                            data = m.replaceAll("").getBytes(StandardCharsets.UTF_8);
                            stripped = true;
                        }
                    }
                    zout.putNextEntry(new ZipEntry(name));
                    zout.write(data);
                }
            }
            return stripped ? out.toByteArray() : kmz;
        } catch (Exception e) {
            log.warn("[wayline] actionUUID 删除失败，按原样导出: {}", e.getMessage());
            return kmz;
        }
    }

    /** 私有化部署对象存储多为自签证书，导出下载仅限内网地址，跳过证书校验 */
    private static javax.net.ssl.SSLContext trustAllContext() {
        try {
            javax.net.ssl.TrustManager[] tm = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {
                        }

                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }
                    }};
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, tm, new java.security.SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("init trust-all ssl failed: " + e.getMessage(), e);
        }
    }

    private WaylineListVO toListVO(WaylineEntity e) {
        return WaylineListVO.builder()
                .id(e.getId())
                .name(e.getName())
                .aircraftModel(e.getAircraftModel())
                .waylineType(e.getWaylineType())
                .status(e.getStatus())
                .fh2WaylineUuid(e.getFh2WaylineUuid())
                .source(e.getSource())
                .folderId(e.getFolderId())
                .updatedAt(e.getUpdatedAt() == null ? null : e.getUpdatedAt().format(FMT))
                .build();
    }

    private WaylineDetailVO toDetailVO(WaylineEntity e, WaylineContentEntity c) {
        WaylineContentVO contentVO = null;
        if (c != null) {
            contentVO = WaylineContentVO.builder()
                    .storageKey(c.getStorageKey())
                    .structuredParams(c.getStructuredParams())
                    .checksum(c.getChecksum())
                    .build();
        }
        return WaylineDetailVO.builder()
                .id(e.getId())
                .name(e.getName())
                .aircraftModel(e.getAircraftModel())
                .waylineType(e.getWaylineType())
                .status(e.getStatus())
                .source(e.getSource())
                .fh2WaylineUuid(e.getFh2WaylineUuid())
                .folderId(e.getFolderId())
                .updatedAt(e.getUpdatedAt() == null ? null : e.getUpdatedAt().format(FMT))
                .content(contentVO)
                .build();
    }

    private Long workspaceId() {
        LoginUser u = UserContext.get();
        return u == null || u.getWorkspaceId() == null ? 1L : u.getWorkspaceId();
    }

    private Long currentUserId() {
        LoginUser u = UserContext.get();
        return u == null ? null : u.getUserId();
    }
}
