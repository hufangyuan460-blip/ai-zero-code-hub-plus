package com.swu.aiZeroCodeHub.generation;

import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.langgraph4j.CodeGenWorkflow;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryAddRequest;
import com.swu.aiZeroCodeHub.model.enums.ChatHistoryMessageTypeEnum;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 增强工作流模式。工作流内部只产生结构化事件，最终 AI 历史由此处统一保存一次。
 */
@Service
@Slf4j
public class WorkflowGenerationStrategy implements GenerationStrategy {

    @Resource
    private CodeGenWorkflow codeGenWorkflow;
    @Resource
    private ChatHistoryService chatHistoryService;

    @Override
    public Flux<GenerationEvent> generate(GenerationRequest request) {
        AtomicBoolean historySaved = new AtomicBoolean(false);
        Flux<GenerationEvent> workflowEvents = codeGenWorkflow.executeWorkflowWithFlux(
                request,
                context -> saveFinalHistory(request, context, historySaved));
        return workflowEvents.onErrorResume(error -> {
            if (Exceptions.unwrap(error) instanceof GenerationCancelledException) {
                return Flux.error(error);
            }
            if (historySaved.compareAndSet(false, true)) {
                saveHistory(request, buildFailureSummary(error), ChatHistoryMessageTypeEnum.AI);
            }
            return Flux.just(GenerationEvent.error(Map.of(
                    "message", readableMessage(error)
            )));
        });
    }

    private void saveFinalHistory(GenerationRequest request,
                                  WorkflowContext context,
                                  AtomicBoolean historySaved) {
        if (!historySaved.compareAndSet(false, true)) {
            return;
        }
        String content = context == null ? null : context.getAiHistoryContent();
        if (context != null && context.getErrorMessage() != null && !context.getErrorMessage().isBlank()) {
            content = context.getErrorMessage();
        }
        if (content == null || content.isBlank()) {
            content = "工作流已完成，但没有可保存的 AI 回复";
        }
        saveHistory(request, content, ChatHistoryMessageTypeEnum.AI);
    }

    private void saveHistory(GenerationRequest request,
                             String content,
                             ChatHistoryMessageTypeEnum messageType) {
        try {
            ChatHistoryAddRequest historyRequest = new ChatHistoryAddRequest();
            historyRequest.setAppId(request.appId());
            historyRequest.setMessageType(messageType.getValue());
            historyRequest.setContent(content);
            chatHistoryService.addChatHistory(historyRequest, request.loginUser());
        } catch (Exception e) {
            log.error("保存工作流对话历史失败，appId={}, 类型={}", request.appId(),
                    e.getClass().getSimpleName());
        }
    }

    private String buildFailureSummary(Throwable error) {
        return "工作流执行失败：" + readableMessage(error);
    }

    private String readableMessage(Throwable error) {
        Throwable cause = error;
        while (cause != null && (cause.getMessage() == null || cause.getMessage().isBlank())) {
            cause = cause.getCause();
        }
        if (cause instanceof BusinessException && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                ? "请稍后重试"
                : cause.getMessage();
    }
}
