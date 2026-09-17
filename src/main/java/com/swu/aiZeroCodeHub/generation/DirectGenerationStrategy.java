package com.swu.aiZeroCodeHub.generation;

import com.swu.aiZeroCodeHub.core.AiCodeGeneratorFacade;
import com.swu.aiZeroCodeHub.core.executor.StreamHandlerExecutor;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 普通模式：复用当前直接调用模型、保存文件和记录 AI 历史的流程。
 */
@Service
public class DirectGenerationStrategy implements GenerationStrategy {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private ChatHistoryService chatHistoryService;

    @Override
    public Flux<GenerationEvent> generate(GenerationRequest request) {
        Flux<String> originFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                request.userMessage(), request.codeGenType(), request.appId());
        Flux<String> handledFlux = streamHandlerExecutor.doExecute(
                originFlux,
                chatHistoryService,
                request.appId(),
                request.loginUser(),
                request.codeGenType());
        return handledFlux.map(GenerationEvent::message);
    }
}
