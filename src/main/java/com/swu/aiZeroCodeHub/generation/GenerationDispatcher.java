package com.swu.aiZeroCodeHub.generation;

import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 代码生成统一分发层。普通模式和工作流模式共用上层鉴权、限流及历史记录流程。
 */
@Service
public class GenerationDispatcher {

    @Resource
    private DirectGenerationStrategy directGenerationStrategy;
    @Resource
    private WorkflowGenerationStrategy workflowGenerationStrategy;

    public Flux<GenerationEvent> generate(GenerationRequest request) {
        if (request == null || request.executionMode() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "执行模式不能为空");
        }
        return switch (request.executionMode()) {
            case DIRECT -> directGenerationStrategy.generate(request);
            case WORKFLOW -> workflowGenerationStrategy.generate(request);
        };
    }
}
