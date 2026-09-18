package com.swu.aiZeroCodeHub.langgraph4j;

import com.swu.aiZeroCodeHub.core.AiCodeGeneratorFacade;
import com.swu.aiZeroCodeHub.core.builder.VueProjectBuilder;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.generation.GenerationEvent;
import com.swu.aiZeroCodeHub.generation.GenerationCancelledException;
import com.swu.aiZeroCodeHub.generation.GenerationRequest;
import com.swu.aiZeroCodeHub.langgraph4j.ai.AiCodeGenTypeRoutingService;
import com.swu.aiZeroCodeHub.langgraph4j.ai.ImageCollectionPlanService;
import com.swu.aiZeroCodeHub.langgraph4j.node.CodeGeneratorNode;
import com.swu.aiZeroCodeHub.langgraph4j.node.CodeQualityCheckNode;
import com.swu.aiZeroCodeHub.langgraph4j.node.ImageCollectorNode;
import com.swu.aiZeroCodeHub.langgraph4j.node.ProjectBuilderNode;
import com.swu.aiZeroCodeHub.langgraph4j.node.PromptEnhancerNode;
import com.swu.aiZeroCodeHub.langgraph4j.node.RouterNode;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import com.swu.aiZeroCodeHub.langgraph4j.tools.ImageSearchTool;
import com.swu.aiZeroCodeHub.langgraph4j.tools.LogoGeneratorTool;
import com.swu.aiZeroCodeHub.langgraph4j.tools.MermaidDiagramTool;
import com.swu.aiZeroCodeHub.langgraph4j.tools.UndrawIllustrationTool;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.generation.GenerationRunProperties;
import com.swu.aiZeroCodeHub.service.GenerationRunStateService;
import com.swu.aiZeroCodeHub.validation.ValidationReport;
import com.swu.aiZeroCodeHub.validation.ValidationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.Exceptions;

import java.util.Map;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 代码生成工作流。工作流自身只发布结构化事件，不拼接 SSE 文本。
 */
@Service
@Slf4j
public class CodeGenWorkflow {

    private static final int DEFAULT_MAX_REPAIR_ATTEMPTS = 2;

    @Resource
    private ImageCollectionPlanService imageCollectionPlanService;
    @Resource
    private ImageSearchTool imageSearchTool;
    @Resource
    private UndrawIllustrationTool undrawIllustrationTool;
    @Resource
    private MermaidDiagramTool mermaidDiagramTool;
    @Resource
    private LogoGeneratorTool logoGeneratorTool;
    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private GenerationRunStateService runStateService;
    @Resource
    private GenerationRunProperties runProperties;
    @Resource
    private ValidationService validationService;

    /**
     * 创建完整工作流。所有节点依赖由 Spring 注入，便于隔离测试。
     */
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            Duration nodeTimeout = effectiveNodeTimeout();
            return new MessagesStateGraph<String>()
                    .addNode("image_collector", ImageCollectorNode.create(
                            imageCollectionPlanService,
                            imageSearchTool,
                            undrawIllustrationTool,
                            mermaidDiagramTool,
                            logoGeneratorTool,
                            nodeTimeout))
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create(aiCodeGenTypeRoutingService, vueProjectBuilder, nodeTimeout))
                    .addNode("code_generator", CodeGeneratorNode.create(aiCodeGeneratorFacade, nodeTimeout))
                    .addNode("code_quality_check", CodeQualityCheckNode.create(validationService, runStateService, nodeTimeout))
                    .addNode("project_builder", ProjectBuilderNode.create(vueProjectBuilder, validationService,
                            runStateService, nodeTimeout))
                    .addEdge(START, "image_collector")
                    .addEdge("image_collector", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    .addEdge("router", "code_generator")
                    .addEdge("code_generator", "code_quality_check")
                    .addConditionalEdges("code_quality_check",
                            edge_async(this::routeAfterQualityCheck),
                            Map.of(
                                    "build", "project_builder",
                                    "skip_build", END,
                                    "retry", "code_generator",
                                    "failed", END
                            ))
                    .addConditionalEdges("project_builder",
                            edge_async(this::routeAfterProjectBuild),
                            Map.of(
                                    "retry", "code_generator",
                                    "completed", END,
                                    "failed", END
                            ))
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "工作流创建失败");
        }
    }

    /**
     * 执行工作流，并将工作流最终上下文交给调用方保存历史。
     */
    public Flux<GenerationEvent> executeWorkflowWithFlux(GenerationRequest request) {
        return executeWorkflowWithFlux(request, ignored -> {
        });
    }

    public Flux<GenerationEvent> executeWorkflowWithFlux(GenerationRequest request,
                                                         Consumer<WorkflowContext> completionHandler) {
        if (request == null || request.appId() == null || request.appId() <= 0) {
            return Flux.error(new BusinessException(ErrorCode.PARAM_ERROR, "工作流 appId 无效"));
        }
        return Flux.create(sink -> {
            AtomicReference<Thread> workerRef = new AtomicReference<>();
            sink.onCancel(() -> {
                Thread worker = workerRef.get();
                if (worker != null) {
                    worker.interrupt();
                }
            });
            Thread worker = Thread.startVirtualThread(() -> {
            WorkflowContext initialContext = WorkflowContext.builder()
                    .appId(request.appId())
                    .userId(request.userId())
                    .originalPrompt(request.userMessage())
                    .enhancedPrompt(request.userMessage())
                    .generationType(request.codeGenType())
                    .executionMode(request.executionMode())
                    .repairAttempt(0)
                    .maxRepairAttempts(effectiveMaxRepairAttempts())
                    .retryCount(0)
                    .maxRetryCount(effectiveMaxRetryCount())
                    .runId(request.runId())
                    .currentStep("初始化")
                    .eventPublisher(sink::next)
                    .cancellationChecker(() -> runStateService != null
                            && runStateService.isCancellationRequested(request.runId()))
                    .llmBudgetChecker(() -> runStateService == null
                            || runStateService.allowLlmCall(request.runId(), effectiveMaxLlmCalls()))
                    .toolBudgetChecker(() -> runStateService == null
                            || runStateService.allowToolCall(request.runId(), effectiveMaxToolCalls()))
                    .build();
            WorkflowContext finalContext = initialContext;
            try {
                finalContext.throwIfCancellationRequested();
                sink.next(GenerationEvent.workflowStart(Map.of(
                        "message", "开始执行增强工作流"
                )));
                CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                for (NodeOutput<MessagesState<String>> step : workflow.stream(
                        Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
                    finalContext.throwIfCancellationRequested();
                    WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                    if (currentContext != null) {
                        finalContext = currentContext;
                    }
                }
                if (finalContext.getErrorMessage() != null && !finalContext.getErrorMessage().isBlank()) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, finalContext.getErrorMessage());
                }
                completionHandler.accept(finalContext);
                sink.next(GenerationEvent.workflowCompleted(Map.of(
                        "message", "增强工作流执行完成"
                )));
                sink.complete();
            } catch (Throwable error) {
                String message = readableMessage(error);
                if (finalContext.getErrorMessage() == null || finalContext.getErrorMessage().isBlank()) {
                    finalContext.setErrorMessage("工作流执行失败：" + message);
                }
                if (!(Exceptions.unwrap(error) instanceof GenerationCancelledException)) {
                    completionHandler.accept(finalContext);
                }
                log.error("工作流执行失败，appId={}, runId={}, error={}",
                        request.appId(), request.runId(), message, error);
                sink.error(error);
            }
            });
            workerRef.set(worker);
        });
    }

    private String routeAfterQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            return "failed";
        }
        context.throwIfCancellationRequested();
        ValidationReport validationReport = context.getValidationReport();
        if (validationReport != null) {
            if (validationReport.passed()) {
                return routeBuildOrSkip(state);
            }
            if (validationReport.repairable()) {
                int repairAttempt = context.getRepairAttempt() == null ? 0 : context.getRepairAttempt();
                int maxRepairAttempts = context.getMaxRepairAttempts() == null ? 1 : context.getMaxRepairAttempts();
                if (repairAttempt < maxRepairAttempts) {
                    int nextAttempt;
                    if (runStateService != null && context.getRunId() != null) {
                                nextAttempt = runStateService.registerRepair(context.getRunId(),
                                        validationReport.fingerprint(), validationReport.artifactHash(),
                                validationReport.affectedFiles(), validationReport.issues().size(), "定向修复");
                        if (nextAttempt == -2) throw new GenerationCancelledException();
                        if (nextAttempt == -3 || nextAttempt == -4 || nextAttempt == -5 || nextAttempt == -6) {
                            context.setErrorMessage("工作流质检修复已停止：问题未产生新的可修复进展");
                            return "failed";
                        }
                    } else {
                        nextAttempt = repairAttempt + 1;
                    }
                    context.setRepairAttempt(nextAttempt);
                    context.setRetryCount(nextAttempt);
                    WorkflowEventSupport.stepStarted(context, "确定性验证修复");
                    WorkflowEventSupport.stepCompleted(context, "确定性验证", "正在进行第 " + nextAttempt + " 次修复");
                    return "retry";
                }
            }
            context.setErrorMessage("工作流在确定性验证阶段失败：" + validationReport.summary());
            return "failed";
        }
        var qualityResult = context.getQualityResult();
        boolean repairable = qualityResult != null
                && Boolean.FALSE.equals(qualityResult.getIsValid())
                && qualityResult.getErrors() != null
                && !qualityResult.getErrors().isEmpty()
                && Boolean.TRUE.equals(context.getQualityFailureRepairable());
        if (repairable) {
            int repairAttempt = context.getRetryCount() == null ? 0 : context.getRetryCount();
            int maxRepairAttempts = context.getMaxRepairAttempts() == null
                    ? effectiveMaxRetryCount() : context.getMaxRepairAttempts();
            if (repairAttempt < maxRepairAttempts) {
                int nextAttempt = repairAttempt + 1;
                if (runStateService != null && context.getRunId() != null) {
                    int persistedAttempt = runStateService.incrementRetry(
                            context.getRunId(), "代码质量检查修复");
                    if (persistedAttempt == -2) {
                        throw new GenerationCancelledException();
                    }
                    if (persistedAttempt < 0) {
                        context.setErrorMessage("工作流质检修复次数已达到上限");
                        return "failed";
                    }
                    nextAttempt = persistedAttempt;
                }
                context.setRetryCount(nextAttempt);
                context.setRepairAttempt(nextAttempt);
                WorkflowEventSupport.stepStarted(context, "代码质量检查修复");
                WorkflowEventSupport.stepCompleted(context, "代码质量检查",
                        "正在进行第 " + nextAttempt + " 次修复");
                return "retry";
            }
            context.setErrorMessage("工作流在代码质检阶段失败：" + summarizeQualityErrors(qualityResult));
            return "failed";
        }
        if (qualityResult == null || !Boolean.TRUE.equals(qualityResult.getIsValid())) {
            context.setErrorMessage("工作流在代码质检阶段失败：" + summarizeQualityErrors(qualityResult));
            return "failed";
        }
        return routeBuildOrSkip(state);
    }

    private String routeBuildOrSkip(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context != null) {
            context.throwIfCancellationRequested();
        }
        CodeGenTypeEnum generationType = context == null ? null : context.getGenerationType();
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            return "build";
        }
        if (context != null) {
            WorkflowEventSupport.stepStarted(context, "项目构建");
            WorkflowEventSupport.stepCompleted(context, "项目构建", "已跳过（非 Vue 项目）");
        }
        return "skip_build";
    }

    private String routeAfterProjectBuild(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            return "failed";
        }
        context.throwIfCancellationRequested();
        ValidationReport validationReport = context.getValidationReport();
        if (validationReport == null || validationReport.passed()) {
            return "completed";
        }
        if (!validationReport.repairable()) {
            context.setErrorMessage("工作流在项目构建阶段失败：" + validationReport.summary());
            return "failed";
        }

        int repairAttempt = context.getRepairAttempt() == null ? 0 : context.getRepairAttempt();
        int maxRepairAttempts = context.getMaxRepairAttempts() == null ? 1 : context.getMaxRepairAttempts();
        if (repairAttempt >= maxRepairAttempts) {
            context.setErrorMessage("工作流项目构建修复次数已达到上限");
            return "failed";
        }

        int nextAttempt = repairAttempt + 1;
        if (runStateService != null && context.getRunId() != null) {
            nextAttempt = runStateService.registerRepair(context.getRunId(),
                    validationReport.fingerprint(), validationReport.artifactHash(),
                    validationReport.affectedFiles(), validationReport.issues().size(), "项目构建定向修复");
            if (nextAttempt == -2) {
                throw new GenerationCancelledException();
            }
            if (nextAttempt == -3 || nextAttempt == -4 || nextAttempt == -5 || nextAttempt == -6) {
                context.setErrorMessage("工作流项目构建修复已停止：问题未产生新的可修复进展");
                return "failed";
            }
            if (nextAttempt <= 0) {
                context.setErrorMessage("工作流项目构建修复次数已达到上限");
                return "failed";
            }
        }
        context.setRepairAttempt(nextAttempt);
        context.setRetryCount(nextAttempt);
        WorkflowEventSupport.stepStarted(context, "项目构建修复");
        WorkflowEventSupport.stepCompleted(context, "项目构建", "正在进行第 " + nextAttempt + " 次修复");
        return "retry";
    }

    private Duration effectiveNodeTimeout() {
        return runProperties == null
                ? Duration.ofMinutes(10)
                : runProperties.effectiveWorkflowNodeTimeout();
    }

    private int effectiveMaxRetryCount() {
        return runProperties == null
                ? DEFAULT_MAX_REPAIR_ATTEMPTS
                : runProperties.effectiveMaxRetryCount();
    }

    private int effectiveMaxRepairAttempts() {
        return runProperties == null ? 1 : runProperties.effectiveMaxRepairAttempts();
    }

    private int effectiveMaxLlmCalls() {
        return runProperties == null ? 2 : runProperties.effectiveMaxLlmCalls();
    }

    private int effectiveMaxToolCalls() {
        return runProperties == null ? 20 : runProperties.effectiveMaxToolCalls();
    }

    private String summarizeQualityErrors(com.swu.aiZeroCodeHub.langgraph4j.model.QualityResult qualityResult) {
        if (qualityResult == null || qualityResult.getErrors() == null || qualityResult.getErrors().isEmpty()) {
            return "未通过质量检查";
        }
        String summary = qualityResult.getErrors().stream()
                .limit(3)
                .map(error -> error == null ? "未知问题" : error)
                .reduce((left, right) -> left + "；" + right)
                .orElse("未通过质量检查");
        return summary.length() > 500 ? summary.substring(0, 500) : summary;
    }

    private String readableMessage(Throwable error) {
        Throwable cause = error;
        while (cause != null && (cause.getMessage() == null || cause.getMessage().isBlank())) {
            cause = cause.getCause();
        }
        return cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                ? "请稍后重试"
                : cause.getMessage();
    }
}
