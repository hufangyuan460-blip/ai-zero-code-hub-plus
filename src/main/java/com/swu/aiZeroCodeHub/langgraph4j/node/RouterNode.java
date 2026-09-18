package com.swu.aiZeroCodeHub.langgraph4j.node;

import com.swu.aiZeroCodeHub.langgraph4j.ai.AiCodeGenTypeRoutingService;
import com.swu.aiZeroCodeHub.core.builder.VueProjectBuilder;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import com.swu.aiZeroCodeHub.generation.GenerationCancelledException;
import com.swu.aiZeroCodeHub.generation.GenerationTimeoutException;
import com.swu.aiZeroCodeHub.langgraph4j.WorkflowEventSupport;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import com.swu.aiZeroCodeHub.utils.SpringContextUtil;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 智能路由工作节点
 */
@Slf4j
public class RouterNode {

    private static final Duration DEFAULT_NODE_TIMEOUT = Duration.ofMinutes(10);

    /** 兼容旧的示例工作流；正式入口使用带依赖参数的工厂方法。 */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return create(SpringContextUtil.getBean(AiCodeGenTypeRoutingService.class));
    }

    public static AsyncNodeAction<MessagesState<String>> create(AiCodeGenTypeRoutingService routingService) {
        return create(routingService, DEFAULT_NODE_TIMEOUT);
    }

    public static AsyncNodeAction<MessagesState<String>> create(AiCodeGenTypeRoutingService routingService,
                                                                 Duration nodeTimeout) {
        return create(routingService, null, nodeTimeout);
    }

    public static AsyncNodeAction<MessagesState<String>> create(AiCodeGenTypeRoutingService routingService,
                                                                 VueProjectBuilder vueProjectBuilder,
                                                                 Duration nodeTimeout) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            context.throwIfCancellationRequested();
            log.info("执行节点: 智能路由");
            WorkflowEventSupport.stepStarted(context, "智能路由");

            CodeGenTypeEnum generationType;
            if (context.getGenerationType() != null) {
                // 用户请求的类型优先于智能路由，保证本次请求参数可预测。
                generationType = context.getGenerationType();
                log.info("使用请求指定的代码生成类型: {}", generationType.getValue());
            } else {
                try {
                    context.checkLlmBudget();
                    // 根据原始提示词进行智能路由
                    generationType = CompletableFuture.supplyAsync(() -> {
                                context.throwIfCancellationRequested();
                                return routingService.routeCodeGenType(context.getOriginalPrompt());
                            })
                            .get(nodeTimeout.toMillis(), TimeUnit.MILLISECONDS);
                log.info("AI智能路由完成，选择类型: {} ({})", generationType.getValue(), generationType.getText());
                } catch (TimeoutException e) {
                    throw new GenerationTimeoutException("智能路由节点超时", e);
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    if (cause instanceof GenerationCancelledException cancellationException) {
                        throw cancellationException;
                    }
                    log.error("AI智能路由失败，使用默认HTML类型: {}", e.getMessage());
                    generationType = CodeGenTypeEnum.HTML;
                }
            }

            // 更新状态
            context.throwIfCancellationRequested();
            if (generationType == CodeGenTypeEnum.VUE_PROJECT
                    && (vueProjectBuilder == null || !vueProjectBuilder.isBuildCapabilityAvailable())) {
                throw new com.swu.aiZeroCodeHub.exception.BusinessException(
                        com.swu.aiZeroCodeHub.exception.ErrorCode.OPERATION_ERROR,
                        "Vue 构建能力未配置，请配置远程隔离构建服务或切换到 HTML/多文件模式");
            }
            context.setCurrentStep("智能路由");
            context.setGenerationType(generationType);
            WorkflowEventSupport.stepCompleted(context, "智能路由", "完成");
            return WorkflowContext.saveContext(context);
        });
    }
}
