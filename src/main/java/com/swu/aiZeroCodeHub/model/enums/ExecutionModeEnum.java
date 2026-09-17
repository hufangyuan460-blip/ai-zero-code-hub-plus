package com.swu.aiZeroCodeHub.model.enums;

import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;

/**
 * 本次代码生成请求的执行模式。
 *
 * <p>该值只作用于当前请求，不持久化到应用表。</p>
 */
public enum ExecutionModeEnum {
    DIRECT,
    WORKFLOW;

    /**
     * 安全解析请求参数。空值表示兼容旧客户端，使用普通模式；未知值直接报参数错误。
     */
    public static ExecutionModeEnum parse(String value) {
        if (value == null || value.isBlank()) {
            return DIRECT;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "executionMode 仅支持 DIRECT 或 WORKFLOW");
        }
    }
}
