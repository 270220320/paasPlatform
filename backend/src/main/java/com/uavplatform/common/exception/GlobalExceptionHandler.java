package com.uavplatform.common.exception;

import com.uavplatform.common.result.ApiResult;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBiz(BizException ex) {
        return ApiResult.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ApiResult<Void> handleBadRequest(Exception ex) {
        String message = "参数校验失败";
        if (ex instanceof MethodArgumentNotValidException manv && manv.getBindingResult().getFieldError() != null) {
            var err = manv.getBindingResult().getFieldError();
            String field = err.getField();
            String detail = err.getDefaultMessage();
            message = (detail == null || detail.isBlank()) ? (field + " 不能为空") : (field + ": " + detail);
        } else if (ex instanceof BindException be && be.getBindingResult().getFieldError() != null) {
            var err = be.getBindingResult().getFieldError();
            String field = err.getField();
            String detail = err.getDefaultMessage();
            message = (detail == null || detail.isBlank()) ? (field + " 不能为空") : (field + ": " + detail);
        } else if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            message = ex.getMessage();
        }
        log.warn("bad request: {}", message);
        return ApiResult.fail(ErrorCodes.BAD_REQUEST, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResult<Void> handleUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("upload size exceeded: {}", ex.getMessage());
        return ApiResult.fail(ErrorCodes.BAD_REQUEST, "上传文件超过大小限制（20MB）");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResult<Void> handleDataIntegrity(DataIntegrityViolationException ex) {
        String raw = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        log.warn("data integrity: {}", raw);
        String message = "数据约束校验失败";
        if (raw != null) {
            if (raw.contains("schedule_config")) {
                message = "缺少 schedule_config 字段，请先执行 alter-mission-schedule-config.sql";
            } else if (raw.contains("cannot be null") || raw.contains("不能为 null") || raw.contains("不能为null")) {
                message = "数据库必填字段为空：" + raw;
            } else {
                message = raw;
            }
        }
        return ApiResult.fail(ErrorCodes.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleOther(Exception ex) {
        log.error("unhandled error", ex);
        return ApiResult.fail(ErrorCodes.INTERNAL, "系统内部错误");
    }
}
