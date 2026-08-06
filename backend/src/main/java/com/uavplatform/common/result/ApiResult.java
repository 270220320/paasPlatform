package com.uavplatform.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应包。
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    private int code;
    private String message;
    private T data;
    private String requestId;

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data, RequestIdHolder.get());
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null, RequestIdHolder.get());
    }
}
