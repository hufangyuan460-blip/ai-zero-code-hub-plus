package com.swu.aiZeroCodeHub.langgraph4j.node;

import com.swu.aiZeroCodeHub.generation.GenerationCancelledException;
import com.swu.aiZeroCodeHub.generation.GenerationTimeoutException;
import com.swu.aiZeroCodeHub.langgraph4j.WorkflowEventSupport;
import com.swu.aiZeroCodeHub.langgraph4j.ai.ImageCollectionPlanService;
import com.swu.aiZeroCodeHub.langgraph4j.model.ImageCollectionPlan;
import com.swu.aiZeroCodeHub.langgraph4j.model.ImageResource;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import com.swu.aiZeroCodeHub.langgraph4j.tools.ImageSearchTool;
import com.swu.aiZeroCodeHub.langgraph4j.tools.LogoGeneratorTool;
import com.swu.aiZeroCodeHub.langgraph4j.tools.MermaidDiagramTool;
import com.swu.aiZeroCodeHub.langgraph4j.tools.UndrawIllustrationTool;
import com.swu.aiZeroCodeHub.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点（并发）。
 */
@Slf4j
public class ImageCollectorNode {

    private static final Duration DEFAULT_NODE_TIMEOUT = Duration.ofMinutes(10);

    /** 兼容旧的示例工作流；正式入口使用带依赖参数的工厂方法。 */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return create(
                SpringContextUtil.getBean(ImageCollectionPlanService.class),
                SpringContextUtil.getBean(ImageSearchTool.class),
                SpringContextUtil.getBean(UndrawIllustrationTool.class),
                SpringContextUtil.getBean(MermaidDiagramTool.class),
                SpringContextUtil.getBean(LogoGeneratorTool.class),
                DEFAULT_NODE_TIMEOUT);
    }

    public static AsyncNodeAction<MessagesState<String>> create(
            ImageCollectionPlanService planService,
            ImageSearchTool imageSearchTool,
            UndrawIllustrationTool illustrationTool,
            MermaidDiagramTool diagramTool,
            LogoGeneratorTool logoTool) {
        return create(planService, imageSearchTool, illustrationTool, diagramTool, logoTool, DEFAULT_NODE_TIMEOUT);
    }

    public static AsyncNodeAction<MessagesState<String>> create(
            ImageCollectionPlanService planService,
            ImageSearchTool imageSearchTool,
            UndrawIllustrationTool illustrationTool,
            MermaidDiagramTool diagramTool,
            LogoGeneratorTool logoTool,
            Duration nodeTimeout) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            context.throwIfCancellationRequested();
            WorkflowEventSupport.stepStarted(context, "资源/图片收集");
            String originalPrompt = context.getOriginalPrompt();
            List<ImageResource> collectedImages = new ArrayList<>();
            boolean completed = true;

            try {
                context.checkLlmBudget();
                ImageCollectionPlan plan = await(CompletableFuture.supplyAsync(() -> {
                    context.throwIfCancellationRequested();
                    return planService.planImageCollection(originalPrompt);
                }), nodeTimeout);
                log.info("获取到图片收集计划，开始并发执行");

                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
                if (plan.getContentImageTasks() != null) {
                    for (ImageCollectionPlan.ImageSearchTask task : plan.getContentImageTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() -> {
                            context.checkToolBudget();
                            context.throwIfCancellationRequested();
                            return imageSearchTool.searchContentImages(task.query());
                        }));
                    }
                }
                if (plan.getIllustrationTasks() != null) {
                    for (ImageCollectionPlan.IllustrationTask task : plan.getIllustrationTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() -> {
                            context.checkToolBudget();
                            context.throwIfCancellationRequested();
                            return illustrationTool.searchIllustrations(task.query());
                        }));
                    }
                }
                if (plan.getDiagramTasks() != null) {
                    for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() -> {
                            context.checkToolBudget();
                            context.throwIfCancellationRequested();
                            return diagramTool.generateMermaidDiagram(task.mermaidCode(), task.description());
                        }));
                    }
                }
                if (plan.getLogoTasks() != null) {
                    for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() -> {
                            context.checkToolBudget();
                            context.throwIfCancellationRequested();
                            return logoTool.generateLogos(task.description());
                        }));
                    }
                }

                CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0]));
                await(allTasks, nodeTimeout);
                for (CompletableFuture<List<ImageResource>> future : futures) {
                    List<ImageResource> images = future.get();
                    if (images != null) {
                        collectedImages.addAll(images);
                    }
                }
                log.info("并发图片收集完成，共收集到 {} 张图片", collectedImages.size());
            } catch (Exception e) {
                Throwable cause = rootCause(e);
                if (cause instanceof GenerationCancelledException cancellationException) {
                    throw cancellationException;
                }
                if (cause instanceof TimeoutException) {
                    throw new GenerationTimeoutException("资源收集节点超时", cause);
                }
                completed = false;
                log.error("图片收集失败: {}", e.getMessage(), e);
            }
            context.throwIfCancellationRequested();
            context.setCurrentStep("图片收集");
            context.setImageList(collectedImages);
            WorkflowEventSupport.stepCompleted(context, "资源/图片收集", completed ? "完成" : "已跳过（资源收集失败）");
            return WorkflowContext.saveContext(context);
        });
    }

    private static <T> T await(CompletableFuture<T> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationCancelledException();
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new GenerationTimeoutException("资源收集节点超时", e);
        } catch (Exception e) {
            Throwable cause = rootCause(e);
            if (cause instanceof GenerationCancelledException cancellationException) {
                throw cancellationException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
