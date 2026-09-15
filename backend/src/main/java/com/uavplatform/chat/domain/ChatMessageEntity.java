package com.uavplatform.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_chat_message")
public class ChatMessageEntity {

    /** tool 消息状态：已执行 */
    public static final String STATUS_OK = "ok";
    /** tool 消息状态：等待人工确认 */
    public static final String STATUS_PENDING = "pending";
    /** tool 消息状态：已确认执行 */
    public static final String STATUS_CONFIRMED = "confirmed";
    /** tool 消息状态：已取消 */
    public static final String STATUS_CANCELLED = "cancelled";
    /** tool 消息状态：执行出错 */
    public static final String STATUS_ERROR = "error";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    /** user / assistant / tool */
    private String role;
    private String content;
    private String thinking;
    private String toolName;
    private String toolArgs;
    private String toolResult;
    private String status;
    private LocalDateTime createdAt;
}
