package com.uavplatform.fh2.dto;

/**
 * 司空航线导入完成事件（importKmz 异步结果推送）。
 *
 * @param asyncRequestId importKmz 返回的异步请求标识，用于关联本地导入记录
 * @param waylineUuid    司空生成的航线 uuid（code=0 时非空）
 * @param kmzObjectKey   司空侧对象存储 key，asyncRequestId 为空时用它关联
 * @param code           司空侧处理结果码，0 成功
 * @param message        失败原因（可空）
 */
public record Fh2WaylineImportedEvent(String asyncRequestId, String waylineUuid, String kmzObjectKey,
                                      int code, String message) {
}
