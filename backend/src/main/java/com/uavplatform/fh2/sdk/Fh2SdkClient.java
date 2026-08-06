package com.uavplatform.fh2.sdk;

import com.uavplatform.fh2.dto.Fh2AircraftLocationVO;
import com.uavplatform.fh2.dto.Fh2CreateFlightTaskCmd;
import com.uavplatform.fh2.dto.Fh2DeviceItemVO;
import com.uavplatform.fh2.dto.Fh2FlightTaskDetailVO;
import com.uavplatform.fh2.dto.Fh2MediaPageVO;
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
     * 按 startIndex（从 0 起）拉取媒体；媒体库倒序翻页时用于从末尾取窗口。
     */
    Fh2MediaPageVO listMediaFilesFromIndex(long startIndex, long pageSize, List<String> fileTypes, String keyword);

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
}
