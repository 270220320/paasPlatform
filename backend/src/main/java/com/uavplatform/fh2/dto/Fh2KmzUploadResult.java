package com.uavplatform.fh2.dto;

/**
 * importKmz 提交结果。司空不同版本可能只回填其一：asyncRequestId 为空时用 kmzObjectKey 关联导入完成事件。
 */
public record Fh2KmzUploadResult(String asyncRequestId, String kmzObjectKey) {

    /** 事件关联键：优先 asyncRequestId，回退 "key:"+kmzObjectKey；都空返回 null */
    public String pendingKey() {
        if (asyncRequestId != null && !asyncRequestId.isBlank()) {
            return asyncRequestId;
        }
        if (kmzObjectKey != null && !kmzObjectKey.isBlank()) {
            return "key:" + kmzObjectKey;
        }
        return null;
    }
}
