package com.swu.aiZeroCodeHub.langgraph4j.node;

import com.swu.aiZeroCodeHub.core.builder.VueProjectBuilder;
import com.swu.aiZeroCodeHub.core.build.BuildExecutionResult;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.generation.GenerationCancelledException;
import com.swu.aiZeroCodeHub.generation.GenerationTimeoutException;
import com.swu.aiZeroCodeHub.langgraph4j.WorkflowEventSupport;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import com.swu.aiZeroCodeHub.validation.ValidationReport;
import com.swu.aiZeroCodeHub.validation.ValidationService;
import com.swu.aiZeroCodeHub.service.GenerationRunStateService;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import com.swu.aiZeroCodeHub.utils.SpringContextUtil;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 项目构建节点。
 */
@Slf4j
public class ProjectBuilderNode {

    private static final Duration DEFAULT_NODE_TIMEOUT = Duration.ofMinutes(10);

    /** 兼容旧的示例工作流；正式入口使用带依赖参数的工厂方法。 */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return create(SpringContextUtil.getBean(VueProjectBuilder.class));
    }

    public static AsyncNodeAction<MessagesState<String>> create(VueProjectBuilder vueBuilder) {
        return create(vueBuilder, DEFAULT_NODE_TIMEOUT);
    }

    public static AsyncNodeAction<MessagesState<String>> create(VueProjectBuilder vueBuilder,
                                                                 Duration nodeTimeout) {
        return create(vueBuilder, null, null, nodeTimeout);
    }

    public static AsyncNodeAction<MessagesState<String>> create(VueProjectBuilder vueBuilder,
                                                                 ValidationService validationService,
                                                                 Duration nodeTimeout) {
        return create(vueBuilder, validationService, null, nodeTimeout);
    }

    public static AsyncNodeAction<MessagesState<String>> create(VueProjectBuilder vueBuilder,
                                                                 ValidationService validationService,
                                                                 GenerationRunStateService runStateService,
                                                                 Duration nodeTimeout) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            context.throwIfCancellationRequested();
            log.info("执行节点: 项目构建，appId={}", context.getAppId());
            WorkflowEventSupport.stepStarted(context, "项目构建");

            String generatedCodeDir = context.getGeneratedCodeDir();
            if (context.getRunId() != null && runStateService != null) {
                int buildCount = runStateService.recordBuildCall(context.getRunId());
                if (buildCount == -2) {
                    throw new GenerationCancelledException();
                }
                if (buildCount < 0) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "项目构建任务状态不可用");
                }
            }
            BuildExecutionResult buildResult;
            try {
                buildResult = CompletableFuture.supplyAsync(() -> {
                            context.throwIfCancellationRequested();
                            return vueBuilder.buildProjectResult(generatedCodeDir);
                        })
                        .get(nodeTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GenerationCancelledException();
            } catch (TimeoutException e) {
                throw new GenerationTimeoutException("项目构建节点超时", e);
            } catch (Exception e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof GenerationCancelledException cancellationException) {
                    throw cancellationException;
                }
                buildResult = new BuildExecutionResult(false, "Vue 项目构建执行失败", -1, false,
                        "EXECUTOR_ERROR");
            }
            context.throwIfCancellationRequested();
            if (buildResult == null) {
                buildResult = new BuildExecutionResult(false, "Vue 项目构建无结果", -1, false,
                        "NO_RESULT");
            }
            ValidationReport validationReport = validationService == null ? null
                    : validationService.validateBuiltArtifact(generatedCodeDir == null ? null : java.nio.file.Path.of(generatedCodeDir),
                    context.getGenerationType(), buildResult);
            if (validationReport != null) {
                context.setValidationReport(validationReport);
                context.setValidationFingerprint(validationReport.fingerprint());
                context.setArtifactHash(validationReport.artifactHash());
                context.setChangedFiles(validationReport.affectedFiles());
                context.setQualityFailureRepairable(validationReport.repairable());
                context.setQualityResult(CodeQualityCheckNode.toQualityResultForBuild(validationReport));
                if (runStateService != null && context.getRunId() != null) {
                    runStateService.updateValidation(context.getRunId(), validationReport.fingerprint(),
                            validationReport.artifactHash(), validationReport.affectedFiles(),
                            validationReport.issues().size());
                }
            }
            if (!buildResult.success() || (validationReport != null && !validationReport.passed())) {
                WorkflowEventSupport.stepCompleted(context, "项目构建", "失败");
                // 将验证结果交给工作流路由。可修复的 NON_ZERO_EXIT 会进入一次
                // 定向修复；不可修复或已达到上限的情况由路由统一安全失败并回滚。
                if (validationReport == null) {
                    context.setErrorMessage("工作流在项目构建阶段失败：Vue 项目构建失败");
                }
                return WorkflowContext.saveContext(context);
            }

            String buildResultDir = generatedCodeDir + File.separator + "dist";
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            WorkflowEventSupport.stepCompleted(context, "项目构建", "完成");
            log.info("项目构建节点完成，最终目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}
