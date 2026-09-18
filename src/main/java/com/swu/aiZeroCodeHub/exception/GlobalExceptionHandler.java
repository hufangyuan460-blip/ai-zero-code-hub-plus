package com.swu.aiZeroCodeHub.exception;

import com.swu.aiZeroCodeHub.common.ResultUtils;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.core.ratelimit.RateLimitException;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Hidden //api文档隐藏注解
@RestControllerAdvice//全局异常处理器
@Slf4j
public class GlobalExceptionHandler {
    //业务异常
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }
    @ExceptionHandler(RateLimitException.class)
    public BaseResponse<?> rateLimitExceptionHandler(RateLimitException e) {
        return ResultUtils.error(ErrorCode.RATE_LIMIT_ERROR, e.getMessage());
    }
    //系统异常
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
    }
}
