package com.swu.aiZeroCodeHub.generation;

import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;

import java.util.UUID;

/**
 * 已完成参数和应用权限校验的代码生成请求。
 */
public record GenerationRequest(
        Long appId,
        Long userId,
        String userMessage,
        CodeGenTypeEnum codeGenType,
        ExecutionModeEnum executionMode,
        User loginUser,
        String runId
) {

    public GenerationRequest(Long appId,
                             Long userId,
                             String userMessage,
                             CodeGenTypeEnum codeGenType,
                             ExecutionModeEnum executionMode) {
        this(appId, userId, userMessage, codeGenType, executionMode, null, null);
    }

    public GenerationRequest {
        if (runId == null || runId.isBlank()) {
            runId = UUID.randomUUID().toString();
        }
    }
}
