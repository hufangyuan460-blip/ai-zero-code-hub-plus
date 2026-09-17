package com.swu.aiZeroCodeHub.langgraph4j.node;

import com.swu.aiZeroCodeHub.constant.AppConstant;
import com.swu.aiZeroCodeHub.core.AiCodeGeneratorFacade;
import com.swu.aiZeroCodeHub.core.streamHandler.HistoryContentAccumulator;
import com.swu.aiZeroCodeHub.core.streamHandler.HistoryContentSanitizer;
import com.swu.aiZeroCodeHub.generation.GenerationEvent;
import com.swu.aiZeroCodeHub.langgraph4j.WorkflowEventSupport;
import com.swu.aiZeroCodeHub.langgraph4j.model.QualityResult;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import com.swu.aiZeroCodeHub.utils.SpringContextUtil;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 网站代码生成节点。
 */
@Slf4j
public class CodeGeneratorNode {

    /**
     * 兼容旧的示例工作流；正式入口使用带依赖参数的工厂方法。
     */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return create(SpringContextUtil.getBean(AiCodeGeneratorFacade.class));
    }

    public static AsyncNodeAction<MessagesState<String>> create(AiCodeGeneratorFacade codeGeneratorFacade) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            WorkflowEventSupport.stepStarted(context, "生成代码");
            log.info("执行节点: 代码生成，appId={}, requestId={}", context.getAppId(), context.getRequestId());

            String userMessage = buildUserMessage(context);
            CodeGenTypeEnum generationType = context.getGenerationType();
            Long appId = context.getAppId();
            if (appId == null || appId <= 0) {
                throw new IllegalArgumentException("工作流 appId 无效");
            }

            HistoryContentAccumulator historyAccumulator = new HistoryContentAccumulator();
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(
                    userMessage, generationType, appId);
            codeStream.doOnNext(chunk -> {
                        // 工作流只通过结构化 message 事件向外输出，Controller 负责 SSE 包装。
                        context.publishEvent(GenerationEvent.message(chunk));
                        historyAccumulator.append(HistoryContentSanitizer.sanitize(chunk, generationType));
                    })
                    .blockLast(Duration.ofMinutes(10));

            String generatedCodeDir = buildCodeDirectory(generationType, appId);
            context.setCurrentStep("生成代码");
            context.setGeneratedCodeDir(generatedCodeDir);
            context.setAiHistoryContent(historyAccumulator.content());
            WorkflowEventSupport.stepCompleted(context, "生成代码", "完成");
            log.info("AI 代码生成完成，appId={}, 目录={}", appId, generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }

    private static String buildCodeDirectory(CodeGenTypeEnum generationType, Long appId) {
        Path path = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR,
                generationType.getValue() + "_" + appId).toAbsolutePath().normalize();
        return path.toString();
    }

    /**
     * 构造用户消息。如果是质检重试，只附加本轮质检结果，避免错误信息无限累积。
     */
    private static String buildUserMessage(WorkflowContext context) {
        String userMessage = context.getEnhancedPrompt();
        if (userMessage == null || userMessage.isBlank()) {
            userMessage = context.getOriginalPrompt();
        }
        QualityResult qualityResult = context.getQualityResult();
        if (isQualityCheckFailed(qualityResult)) {
            userMessage = userMessage + buildErrorFixPrompt(qualityResult);
        }
        return userMessage;
    }

    private static boolean isQualityCheckFailed(QualityResult qualityResult) {
        return qualityResult != null
                && Boolean.FALSE.equals(qualityResult.getIsValid())
                && qualityResult.getErrors() != null
                && !qualityResult.getErrors().isEmpty();
    }

    private static String buildErrorFixPrompt(QualityResult qualityResult) {
        StringBuilder errorInfo = new StringBuilder("\n\n## 上次生成的代码存在以下问题，请修复：\n");
        qualityResult.getErrors().stream().limit(20)
                .forEach(error -> errorInfo.append("- ").append(error).append("\n"));
        if (qualityResult.getSuggestions() != null && !qualityResult.getSuggestions().isEmpty()) {
            errorInfo.append("\n## 修复建议：\n");
            qualityResult.getSuggestions().stream().limit(20)
                    .forEach(suggestion -> errorInfo.append("- ").append(suggestion).append("\n"));
        }
        return errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。")
                .toString();
    }
}
