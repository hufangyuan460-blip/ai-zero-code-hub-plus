package com.swu.aiZeroCodeHub.langgraph4j.state;

import com.swu.aiZeroCodeHub.langgraph4j.model.ImageCollectionPlan;
import com.swu.aiZeroCodeHub.langgraph4j.model.ImageResource;
import com.swu.aiZeroCodeHub.langgraph4j.model.QualityResult;
import com.swu.aiZeroCodeHub.generation.GenerationEvent;
import com.swu.aiZeroCodeHub.generation.GenerationCancelledException;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;
import com.swu.aiZeroCodeHub.validation.ValidationReport;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 工作流上下文 - 存储所有状态信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {

    /**
     * WorkflowContext 在 MessagesState 中的存储key
     */
    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    /**
     * 当前执行步骤
     */
    private String currentStep;

    /**
     * 已经完成鉴权的应用和用户标识，所有工作流资源都必须使用该 appId。
     */
    private Long appId;
    private Long userId;

    /**
     * 本次请求的执行参数。
     */
    private CodeGenTypeEnum generationType;
    private ExecutionModeEnum executionMode;
    @Builder.Default
    private Integer repairAttempt = 0;
    @Builder.Default
    private Integer maxRepairAttempts = 2;
    private String runId;
    @Builder.Default
    private Integer retryCount = 0;
    @Builder.Default
    private Integer maxRetryCount = 2;

    /**
     * 用户原始输入的提示词
     */
    private String originalPrompt;

    /**
     * 图片资源字符串
     */
    private String imageListStr;

    /**
     * 图片资源列表
     */
    private List<ImageResource> imageList;

    /**
     * 增强后的提示词
     */
    private String enhancedPrompt;

    /**
     * 生成的代码目录
     */
    private String generatedCodeDir;

    /**
     * 构建成功的目录
     */
    private String buildResultDir;

    /**
     * 质量检查结果
     */
    private QualityResult qualityResult;
    private Boolean qualityFailureRepairable;

    /** 确定性验证和运行预算状态，不保存完整源码或工具参数。 */
    private ValidationReport validationReport;
    private String validationFingerprint;
    @Builder.Default
    private Set<String> seenFingerprints = Set.of();
    private String artifactHash;
    @Builder.Default
    private List<String> changedFiles = List.of();
    @Builder.Default
    private Integer llmCallCount = 0;
    @Builder.Default
    private Integer toolCallCount = 0;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 最终写入聊天历史的安全摘要。工具参数和完整源码不会进入该字段。
     */
    private String aiHistoryContent;

    /**
     * 仅用于本次执行期间向 Flux 发布结构化事件，不参与工作流持久化。
     */
    private transient Consumer<GenerationEvent> eventPublisher;

    /**
     * 运行治理回调，不参与工作流状态持久化。
     */
    private transient BooleanSupplier cancellationChecker;
    private transient BooleanSupplier llmBudgetChecker;
    private transient BooleanSupplier toolBudgetChecker;

    /**
     * 图片收集计划
     */
    private ImageCollectionPlan imageCollectionPlan;

    /**
     * 并发图片收集的中间结果字段
     */
    private List<ImageResource> contentImages;
    private List<ImageResource> illustrations;
    private List<ImageResource> diagrams;
    private List<ImageResource> logos;

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 上下文操作方法 ==========

    /**
     * 从 MessagesState 中获取 WorkflowContext
     */
    public static WorkflowContext getContext(MessagesState<String> state) {
        return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
    }

    /**
     * 将 WorkflowContext 保存到 MessagesState 中
     */
    public static Map<String, Object> saveContext(WorkflowContext context) {
        return Map.of(WORKFLOW_CONTEXT_KEY, context);
    }

    public void publishEvent(GenerationEvent event) {
        if (eventPublisher != null && event != null) {
            eventPublisher.accept(event);
        }
    }

    public boolean isCancellationRequested() {
        return cancellationChecker != null && cancellationChecker.getAsBoolean();
    }

    public void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new GenerationCancelledException();
        }
    }

    public void checkLlmBudget() {
        throwIfCancellationRequested();
        if (llmBudgetChecker != null && !llmBudgetChecker.getAsBoolean()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "模型调用预算已用尽");
        }
    }

    public void checkToolBudget() {
        throwIfCancellationRequested();
        if (toolBudgetChecker != null && !toolBudgetChecker.getAsBoolean()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "工具调用预算已用尽");
        }
    }
}
