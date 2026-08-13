package com.uavplatform.fh2.dto;

import lombok.Builder;
import lombok.Data;

/**
 * TerraBuild 模型文件条目（SDK getModelFileList）。
 */
@Data
@Builder
public class Fh2TerraModelVO {

    private Integer id;
    private String uuid;
    private String name;
    /**
     * 司空建图文件状态（TerraBuildModelFileItem.state）：
     * 8=建图失败，14=重建中，15=重建成功，2=ZIP 压缩成功，3=ZIP 压缩失败 等。
     */
    private Integer state;
    private String thumbnailUrl;
    private String zipUrl;
}
