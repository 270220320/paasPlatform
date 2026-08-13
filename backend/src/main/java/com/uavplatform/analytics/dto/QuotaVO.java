package com.uavplatform.analytics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuotaVO {

    /** private | saas */
    private String mode;
    /** 存储上限；0 表示不限（私有化常见） */
    private Long storageTotalBytes;
    private Long storageUsedBytes;
    /** 建图张数上限；0 表示不限。司空云端二三维重建按「张」扣配额 */
    private Integer mappingQuota;
    private Integer mappingUsed;
    /**
     * 直播分钟上限。null 表示本平台未统计（驾驶舱观看由司空侧计量，AiFlow 未接入）。
     */
    private Integer livestreamMinutesTotal;
    private Integer livestreamMinutesUsed;
}
