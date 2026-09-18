package com.swu.aiZeroCodeHub.generation;

import com.swu.aiZeroCodeHub.core.AiCodeGeneratorFacade;
import com.swu.aiZeroCodeHub.core.executor.StreamHandlerExecutor;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import com.swu.aiZeroCodeHub.service.GenerationRunStateService;
import com.swu.aiZeroCodeHub.validation.ValidationReport;
import com.swu.aiZeroCodeHub.validation.ValidationService;
import com.swu.aiZeroCodeHub.constant.AppConstant;
import java.nio.file.Path;
import java.util.Map;
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
    @Resource
    private GenerationRunStateService runStateService;
    @Resource
    private GenerationRunProperties runProperties;
    @Resource
    private ValidationService validationService;

    @Override
    public Flux<GenerationEvent> generate(GenerationRequest request) {
        int maxLlmCalls = runProperties == null ? 2 : runProperties.effectiveMaxLlmCalls();
        int maxToolCalls = runProperties == null ? 20 : runProperties.effectiveMaxToolCalls();
        if (runStateService != null && !runStateService.allowLlmCall(request.runId(), maxLlmCalls)) {
            return Flux.error(new com.swu.aiZeroCodeHub.exception.BusinessException(
                    com.swu.aiZeroCodeHub.exception.ErrorCode.OPERATION_ERROR, "模型调用预算已用尽"));
        }
        Flux<String> originFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                request.userMessage(), request.codeGenType(), request.appId());
        Flux<String> handledFlux = streamHandlerExecutor.doExecute(
                originFlux,
                chatHistoryService,
                request.appId(),
                request.loginUser(),
                request.codeGenType(),
                () -> runStateService != null && runStateService.isCancellationRequested(request.runId()),
                () -> runStateService == null || runStateService.allowToolCall(request.runId(), maxToolCalls));
        Flux<GenerationEvent> events = handledFlux.map(GenerationEvent::message);
        return events.concatWith(Flux.defer(() -> {
            if (validationService == null) {
                return Flux.empty();
            }
            Path outputDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR,
                    request.codeGenType().getValue() + "_" + request.appId());
            ValidationReport report = validationService.validate(outputDir, request.codeGenType());
            if (runStateService != null) {
                runStateService.updateValidation(request.runId(), report.fingerprint(), report.artifactHash(),
                        report.affectedFiles(), report.issues().size());
            }
            if (report.passed()) {
                return Flux.empty();
            }
            return Flux.just(GenerationEvent.error(Map.of(
                    "message", "生成结果未通过确定性验证：" + report.summary())));
        }));
    }
}
