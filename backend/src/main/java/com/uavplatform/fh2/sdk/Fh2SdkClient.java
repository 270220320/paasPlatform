package com.uavplatform.fh2.sdk;

import com.uavplatform.fh2.dto.Fh2AircraftLocationVO;
import com.uavplatform.fh2.dto.Fh2CreateFlightTaskCmd;
import com.uavplatform.fh2.dto.Fh2DeviceItemVO;
import com.uavplatform.fh2.dto.Fh2FlightTaskDetailVO;
import com.uavplatform.fh2.dto.Fh2MediaPageVO;
import com.uavplatform.fh2.dto.Fh2TerraModelVO;
import com.uavplatform.fh2.dto.Fh2WaylineItemVO;

import java.util.List;
import java.util.Optional;

/**
 * FlightHub Java SDK 门面。实现可走原生 SDK（反射）或降级空实现。
 */
public interface Fh2SdkClient {

    boolean available();

    Optional<String> initError();

    List<Fh2DeviceItemVO> listDevices();

    /**
     * 飞行器列表（含机场/网关下挂载的子设备）。
     * 拓扑里常只有 dock，本地库同步后也常缺 aircraft，看板计数应优先走此接口。
     */
    default List<Fh2DeviceItemVO> listAircraft() {
        return listDevices().stream()
                .filter(d -> d != null && d.getDeviceType() != null)
                .filter(d -> {
                    String t = d.getDeviceType().trim().toLowerCase();
                    return "aircraft".equals(t) || "drone".equals(t);
                })
                .toList();
    }

    /**
     * 拉取当前在飞飞行器的实时位置（SDK listenAircraftLocation + modeCode）。
     * 候选机队取自司空设备拓扑中的 aircraft。
     */
    List<Fh2AircraftLocationVO> listFlyingAircraftLocations();

    /**
     * 对给定飞行器候选列表拉取在飞位置（通常来自本地设备表）。
     */
    List<Fh2AircraftLocationVO> listFlyingAircraftLocations(List<Fh2DeviceItemVO> aircraft);

    List<Fh2WaylineItemVO> listWaylines(int page, int pageSize);

    void updateWaylineName(String waylineUuid, String name);

    void deleteWaylines(List<String> waylineUuids);

    /**
     * 媒体文件列表。fileTypes 为空时默认查图片+视频（"2","3"）。
     * page 从 1 开始（司空侧按创建时间正序分页）。
     */
    Fh2MediaPageVO listMediaFiles(long page, long pageSize, List<String> fileTypes, String keyword);

    /**
     * 按飞行任务 UUID（flightId）过滤的媒体列表。
     */
    default Fh2MediaPageVO listMediaFilesByFlightId(String flightId, long page, long pageSize,
                                                    List<String> fileTypes, String keyword) {
        return listMediaFiles(page, pageSize, fileTypes, keyword);
    }

    /**
     * 按 startIndex（从 0 起）拉取媒体；媒体库倒序翻页时用于从末尾取窗口。
     */
    Fh2MediaPageVO listMediaFilesFromIndex(long startIndex, long pageSize, List<String> fileTypes, String keyword);

    /**
     * 按 startIndex + flightId 拉取媒体。
     */
    default Fh2MediaPageVO listMediaFilesFromIndex(long startIndex, long pageSize, List<String> fileTypes,
                                                   String keyword, String flightId) {
        return listMediaFilesFromIndex(startIndex, pageSize, fileTypes, keyword);
    }

    /** 按司空 fileId 取下载地址 */
    String getMediaDownloadUrl(long fileId);

    /** 批量删除司空媒体 */
    void deleteMediaFiles(List<Long> fileIds);

    /**
     * FlightTaskModule.createFlightTask — 创建航线任务（立即/条件），返回 taskUuid。
     * 当前 jar 无独立 executeFlightTask；立即任务（taskType=1）创建后由云端触发执行。
     */
    String createFlightTask(Fh2CreateFlightTaskCmd cmd);

    /** FlightTaskModule.getFlightTaskDetail */
    Fh2FlightTaskDetailVO getFlightTaskDetail(String taskUuid);

    /** FlightTaskModule.deleteFlightTask */
    void deleteFlightTask(String taskUuid);

    /**
     * TerraBuildModule.createReconstruction — 私有化三维重建（SDK，不走 OpenAPI）。
     *
     * @param name      模型名
     * @param folderIds 媒体文件夹 ID（建议同时传「照片所在子目录」与「飞行任务夹」）
     * @return 司空模型数字 ID
     */
    default long createTerraReconstruction(String name, java.util.List<Integer> folderIds) {
        return createTerraReconstruction(name, folderIds, java.util.List.of());
    }

    /**
     * 与 demo {@code TerraBuildModule.createReconstruction} 一致：有 folderUuid 时优先传 UUID。
     */
    default long createTerraReconstruction(String name, java.util.List<Integer> folderIds,
                                           java.util.List<String> folderUuids) {
        throw new UnsupportedOperationException("TerraBuild 重建未实现");
    }

    /** @deprecated 使用 {@link #createTerraReconstruction(String, java.util.List)} */
    default long createTerraReconstruction(String name, long folderId) {
        return createTerraReconstruction(name, java.util.List.of((int) folderId));
    }

    /** TerraBuildModule.getModelFileList */
    default List<Fh2TerraModelVO> listTerraModels(int page, int pageSize) {
        throw new UnsupportedOperationException("TerraBuild 模型列表未实现");
    }

    /** TerraBuildModule.getModelFileUrl(fileId, type) type: 1=原始zip 等 */
    default String getTerraModelFileUrl(int modelId, int type) {
        throw new UnsupportedOperationException("TerraBuild 模型地址未实现");
    }

    /**
     * TerraBuildModule.getReconstructionSettings — 查询已有模型的 folderId→folderUuid 映射。
     * 返回该模型关联的文件夹映射（可能为空）。
     */
    default java.util.Map<Integer, String> getTerraFolderUuidMap(String modelUuid) {
        return java.util.Map.of();
    }

    /**
     * 从已有重建任务中反查 folderUuid：遍历最近模型，返回 folderId 匹配的 folderUuid。
     * 仅当模型 folderList 中存在与 {@code targetFolderIds} 相同的 folderId 时，
     * 才返回对应的 folderUuid，避免拿到其他飞行任务的错误 UUID。
     *
     * @param targetFolderIds 当前飞行任务的媒体文件夹 ID（如 [103, 102, 83]）
     * @return 匹配到的 folderUuid；查不到返回 null
     */
    default String resolveTerraFolderUuidByFolderIds(java.util.Collection<Integer> targetFolderIds) {
        return null;
    }

    /**
     * 查询重建回调中缓存的错误码（reconstructionStatusChanged 推送的 code）。
     * 返回 0 表示无异常或尚未收到推送。
     */
    default int getReconstructionErrorCode(long fileId) {
        return 0;
    }

    /**
     * TerraBuildModule.getReconstructionImages — 查询重建引擎实际收到的图像数量。
     * 用于诊断重建失败时照片是否被正确关联。
     */
    default int getReconstructionImageCount(String modelUuid) {
        return -1;
    }
}
