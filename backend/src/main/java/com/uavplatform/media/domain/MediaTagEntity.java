package com.uavplatform.media.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_media_tag")
public class MediaTagEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long mediaId;
    private String tag;
    private LocalDateTime createdAt;
}
