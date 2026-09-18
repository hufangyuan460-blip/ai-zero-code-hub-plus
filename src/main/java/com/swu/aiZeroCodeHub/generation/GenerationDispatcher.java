package com.swu.aiZeroCodeHub.generation;

import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;
import com.swu.aiZeroCodeHub.service.GenerationRunStateService;
import com.swu.aiZeroCodeHub.service.GenerationAppLockService;
import com.swu.aiZeroCodeHub.service.ArtifactBackupService;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 代码生成统一分发层及运行治理执行器。
 *
 * <p>执行流通过独立的 replay sink 与 SSE 订阅解耦。浏览器断开只会失去事件订阅，
 * 不会向本次服务端运行发送取消信号；只有显式取消接口会设置 Redis 取消标记。</p>
 */
@Service
@Slf4j
public class GenerationDispatcher {

    private static final int REPLAY_LIMIT = 512;

    @Resource
    private DirectGenerationStrategy directGenerationStrategy;
    @Resource
    private WorkflowGenerationStrategy workflowGenerationStrategy;
    @Resource
    private GenerationRunStateService runStateService;
    @Resource
    private GenerationRunProperties runProperties;
    @Resource
    private GenerationAppLockService appLockService;
    @Resource
    private ArtifactBackupService artifactBackupService;

    private final Map<String, Sinks.Many<GenerationEvent>> eventSinks = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> eventSequences = new ConcurrentHashMap<>();
    private final ScheduledExecutorService eventCleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "agent-run-event-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * AppService 已创建 PENDING 状态后调用此方法。返回流本身不会暴露内部异常堆栈。
     */
    public Flux<GenerationEvent> generate(GenerationRequest request) {
        return start(request);
    }

    /**
     * 启动一次已经完成鉴权、锁定和状态创建的运行。该方法不依赖 SSE 订阅者。
     */
    public Flux<GenerationEvent> start(GenerationRequest request) {
        if (request == null || request.executionMode() == null || request.runId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生成运行信息无效");
        }
        Sinks.Many<GenerationEvent> eventSink = eventSinks.computeIfAbsent(request.runId(), ignored -> {
            Sinks.Many<GenerationEvent> created = Sinks.many().replay().limit(REPLAY_LIMIT);
            AtomicBoolean terminal = new AtomicBoolean(false);
            Thread.startVirtualThread(() -> execute(request, created, terminal));
            return created;
        });
        return eventSink.asFlux();
    }

    /**
     * 订阅已经启动的运行；不会重新执行策略。
     */
    public Flux<GenerationEvent> subscribe(String runId) {
        return subscribe(runId, 0L);
    }

    /**
     * 只回放客户端确认序号之后的事件。序号早于回放窗口时发送明确的恢复事件，
     * 客户端可先查询 Redis 状态，再从当前窗口继续订阅。
     */
    public Flux<GenerationEvent> subscribe(String runId, long afterSequence) {
        Sinks.Many<GenerationEvent> eventSink = eventSinks.get(runId);
        if (eventSink != null) {
            long lastSequence = eventSequences.getOrDefault(runId, new AtomicLong(0L)).get();
            long firstAvailableSequence = Math.max(1L, lastSequence - REPLAY_LIMIT + 1L);
            Flux<GenerationEvent> replay = eventSink.asFlux()
                    .filter(event -> event.sequence() > afterSequence);
            if (afterSequence > 0L && afterSequence < firstAvailableSequence - 1L) {
                GenerationRunState state = runStateService.getRequired(runId);
                Map<String, Object> recoveryPayload = payloadForState(state, "SSE 回放窗口已过期，请查询运行状态后继续");
                recoveryPayload.put("replayWindowExpired", true);
                recoveryPayload.put("firstAvailableSequence", firstAvailableSequence);
                recoveryPayload.put("lastSequence", lastSequence);
                return Flux.concat(
                        Flux.just(new GenerationEvent("replay_reset", recoveryPayload,
                                firstAvailableSequence - 1L)),
                        replay
                );
            }
            return replay;
        }
        GenerationRunState state = runStateService.getRequired(runId);
        if (state.isActive()) {
            return Flux.just(new GenerationEvent("run_started", payloadForState(state, "任务已在运行中")));
        }
        return terminalReplay(state);
    }

    private void execute(GenerationRequest request,
                         Sinks.Many<GenerationEvent> eventSink,
                         AtomicBoolean terminal) {
        try {
            if (!runStateService.transitionToRunning(request.runId(), "代码生成")) {
                finish(request, eventSink, terminal, GenerationRunStatus.FAILED, "生成任务无法启动");
                return;
            }
            emit(eventSink, request, GenerationEvent.runStarted("开始生成代码"));
            log.info("生成运行开始，runId={}, appId={}, mode={}", request.runId(), request.appId(), request.executionMode());

            if (runStateService.isCancellationRequested(request.runId())) {
                finish(request, eventSink, terminal, GenerationRunStatus.CANCELLED, "生成任务已取消");
                return;
            }

            Duration timeout = request.executionMode() == ExecutionModeEnum.WORKFLOW
                    ? effectiveWorkflowTimeout()
                    : effectiveDirectTimeout();
            Flux<GenerationEvent> source = Flux.defer(() -> selectStrategy(request))
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(timeout);
            source.subscribe(
                    event -> handleEvent(request, eventSink, terminal, event),
                    error -> finish(request, eventSink, terminal, statusFor(request, error), safeErrorMessage(error)),
                    () -> finish(request, eventSink, terminal, GenerationRunStatus.SUCCEEDED, null)
            );
        } catch (Throwable error) {
            finish(request, eventSink, terminal, statusFor(request, error), safeErrorMessage(error));
        }
    }

    private Flux<GenerationEvent> selectStrategy(GenerationRequest request) {
        return switch (request.executionMode()) {
            case DIRECT -> directGenerationStrategy.generate(request);
            case WORKFLOW -> workflowGenerationStrategy.generate(request);
        };
    }

    private void handleEvent(GenerationRequest request,
                             Sinks.Many<GenerationEvent> eventSink,
                             AtomicBoolean terminal,
                             GenerationEvent event) {
        if (terminal.get()) {
            return;
        }
        if (runStateService.isCancellationRequested(request.runId())) {
            finish(request, eventSink, terminal, GenerationRunStatus.CANCELLED, "生成任务已取消");
            return;
        }
        if (event == null) {
            return;
        }
        // 兼容尚未升级的内部策略，但对外始终只发送 generation_error。
        if ("generation_error".equals(event.type()) || "error".equals(event.type())) {
            finish(request, eventSink, terminal, GenerationRunStatus.FAILED, eventMessage(event));
            return;
        }
        if ("cancelled".equals(event.type())) {
            finish(request, eventSink, terminal, GenerationRunStatus.CANCELLED, eventMessage(event));
            return;
        }
        updateStep(request, event);
        emit(eventSink, request, event);
    }

    private void finish(GenerationRequest request,
                        Sinks.Many<GenerationEvent> eventSink,
                        AtomicBoolean terminal,
                        GenerationRunStatus desiredStatus,
                        String errorMessage) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        try {
            runStateService.finish(request.runId(), desiredStatus, errorMessage);
            GenerationRunState finalState = runStateService.getRequired(request.runId());
            if (artifactBackupService != null) {
                if (finalState.status() == GenerationRunStatus.SUCCEEDED) {
                    artifactBackupService.commit(request.appId(), request.runId());
                } else {
                    artifactBackupService.restore(request.appId(), request.runId());
                }
            }
            if (finalState.status() == GenerationRunStatus.CANCELLED) {
                emit(eventSink, request, GenerationEvent.cancelled("生成任务已取消"));
            } else if (finalState.status() == GenerationRunStatus.FAILED
                    || finalState.status() == GenerationRunStatus.TIMED_OUT) {
                emit(eventSink, request, GenerationEvent.generationError(finalState.errorMessage()));
            }
            emit(eventSink, request, GenerationEvent.done("运行结束"));
            eventSink.tryEmitComplete();
            log.info("生成运行结束，runId={}, appId={}, status={}", request.runId(), request.appId(), finalState.status());
            eventCleanupExecutor.schedule(() -> {
                        eventSinks.remove(request.runId(), eventSink);
                        eventSequences.remove(request.runId());
                    },
                    24, TimeUnit.HOURS);
        } catch (Throwable finishError) {
            log.error("更新生成运行终态失败，runId={}", request.runId(), finishError);
            eventSink.tryEmitError(new BusinessException(ErrorCode.SYSTEM_ERROR, "生成任务状态更新失败"));
        } finally {
            if (appLockService != null) {
                appLockService.release(request.appId(), request.runId());
            }
        }
    }

    private void emit(Sinks.Many<GenerationEvent> eventSink, GenerationRequest request, GenerationEvent event) {
        GenerationRunState state = runStateService.find(request.runId()).orElse(null);
        if (state == null) {
            return;
        }
        long sequence = eventSequences.computeIfAbsent(request.runId(), ignored -> new AtomicLong(0L))
                .incrementAndGet();
        eventSink.tryEmitNext(new GenerationEvent(event.type(), buildPayload(state, event, sequence), sequence));
    }

    private Map<String, Object> buildPayload(GenerationRunState state, GenerationEvent event, long sequence) {
        Map<String, Object> payload = payloadForState(state, safeMessage(eventMessage(event)));
        payload.put("sequence", sequence);
        addSafeEventFields(payload, event.data());
        return payload;
    }

    private Map<String, Object> payloadForState(GenerationRunState state, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", state.runId());
        payload.put("appId", String.valueOf(state.appId()));
        payload.put("executionMode", state.executionMode().name());
        payload.put("status", state.status().name());
        payload.put("currentStep", state.currentStep());
        payload.put("message", safeMessage(message));
        payload.put("retryCount", state.retryCount());
        payload.put("maxRetryCount", state.maxRetryCount());
        payload.put("repairAttempt", state.repairAttempt());
        payload.put("maxRepairAttempts", state.maxRepairAttempts());
        payload.put("llmCallCount", state.llmCallCount());
        payload.put("toolCallCount", state.toolCallCount());
        payload.put("buildCallCount", state.buildCallCount());
        payload.put("validationFingerprint", state.validationFingerprint());
        payload.put("artifactHash", state.artifactHash());
        payload.put("changedFiles", state.changedFiles() == null ? java.util.List.of() : state.changedFiles());
        payload.put("validationIssueCount", state.validationIssueCount());
        return payload;
    }

    private Flux<GenerationEvent> terminalReplay(GenerationRunState state) {
        if (state.status() == GenerationRunStatus.CANCELLED) {
            return Flux.just(
                    new GenerationEvent("cancelled", payloadForState(state, "生成任务已取消")),
                    new GenerationEvent("done", payloadForState(state, "运行结束")));
        }
        if (state.status() == GenerationRunStatus.FAILED || state.status() == GenerationRunStatus.TIMED_OUT) {
            return Flux.just(
                    new GenerationEvent("generation_error", payloadForState(state,
                            state.errorMessage() == null ? "生成失败，请稍后重试" : state.errorMessage())),
                    new GenerationEvent("done", payloadForState(state, "运行结束")));
        }
        return Flux.just(new GenerationEvent("done", payloadForState(state, "运行结束")));
    }

    private void addSafeEventFields(Map<String, Object> payload, Object data) {
        if (!(data instanceof Map<?, ?> map)) {
            return;
        }
        copyStringField(payload, map, "step");
        copyStringField(payload, map, "status");
        copyStringField(payload, map, "toolName");
        copyStringField(payload, map, "filePath");
    }

    private void copyStringField(Map<String, Object> payload, Map<?, ?> data, String key) {
        Object value = data.get(key);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            payload.put(key, safeMessage(stringValue));
        }
    }

    private void updateStep(GenerationRequest request, GenerationEvent event) {
        String step = null;
        if (event.data() instanceof Map<?, ?> map && map.get("step") instanceof String value) {
            step = value;
        } else if ("workflow_start".equals(event.type()) || "workflow_completed".equals(event.type())) {
            step = "增强工作流";
        } else if ("message".equals(event.type())) {
            step = "代码生成";
        }
        if (step != null) {
            runStateService.updateCurrentStep(request.runId(), step);
        }
    }

    private GenerationRunStatus statusFor(GenerationRequest request, Throwable error) {
        if (runStateService.isCancellationRequested(request.runId())) {
            return GenerationRunStatus.CANCELLED;
        }
        Throwable cause = Exceptions.unwrap(error);
        if (cause instanceof GenerationCancelledException) {
            return GenerationRunStatus.CANCELLED;
        }
        if (cause instanceof TimeoutException || cause instanceof GenerationTimeoutException) {
            return GenerationRunStatus.TIMED_OUT;
        }
        return GenerationRunStatus.FAILED;
    }

    private String eventMessage(GenerationEvent event) {
        if (event == null || event.data() == null) {
            return "请稍后重试";
        }
        if (event.data() instanceof Map<?, ?> map && map.get("message") != null) {
            return String.valueOf(map.get("message"));
        }
        return String.valueOf(event.data());
    }

    private String safeErrorMessage(Throwable error) {
        Throwable cause = Exceptions.unwrap(error);
        if (cause instanceof GenerationCancelledException) {
            return "生成任务已取消";
        }
        if (cause instanceof TimeoutException || cause instanceof GenerationTimeoutException) {
            return "生成任务超时，请稍后重试";
        }
        if (cause instanceof BusinessException businessException
                && businessException.getMessage() != null
                && !businessException.getMessage().isBlank()) {
            return safeMessage(businessException.getMessage());
        }
        return "生成失败，请稍后重试";
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "处理中";
        }
        String sanitized = message.replaceAll("(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*[^,;\\s]+", "$1=***")
                .replaceAll("[A-Za-z]:\\\\[^\\n\\r]*|/(?:[^\\n\\r ]+/)+[^\\n\\r ]*", "[路径]");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private Duration effectiveDirectTimeout() {
        return runProperties == null ? Duration.ofMinutes(10) : runProperties.effectiveDirectTimeout();
    }

    private Duration effectiveWorkflowTimeout() {
        return runProperties == null ? Duration.ofMinutes(20) : runProperties.effectiveWorkflowTimeout();
    }

    @PreDestroy
    public void shutdown() {
        eventCleanupExecutor.shutdownNow();
    }
}
