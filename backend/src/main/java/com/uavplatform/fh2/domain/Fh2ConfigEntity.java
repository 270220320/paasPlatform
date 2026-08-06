package com.uavplatform.fh2.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_fh2_config")
public class Fh2ConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workspaceId;
    private Integer enabled;
    private String frontHost;
    private String serverHost;
    private String gatewayBase;
    private String wssUrl;
    private String userToken;
    private String bindingUuid;
    private String sdkAddress;
    private String sdkPort;
    private String waylineSdkCss;
    private String waylineSdkJs;
    private String deviceSdkCss;
    private String deviceSdkJs;
    private String extraHttpHeaderKey;
    private String extraHttpHeaderValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
