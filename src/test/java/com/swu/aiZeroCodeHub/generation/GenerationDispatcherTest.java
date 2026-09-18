package com.swu.aiZeroCodeHub.generation;

import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;
import com.swu.aiZeroCodeHub.service.GenerationRunStateService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationDispatcherTest {

    @Test
    void dispatchesEachModeToItsOwnStrategy() {
        DirectGenerationStrategy direct = mock(DirectGenerationStrategy.class);
        WorkflowGenerationStrategy workflow = mock(WorkflowGenerationStrategy.class);
        GenerationDispatcher dispatcher = new GenerationDispatcher();
        ReflectionTestUtils.setField(dispatcher, "directGenerationStrategy", direct);
        ReflectionTestUtils.setField(dispatcher, "workflowGenerationStrategy", workflow);
        TestRunStateService runStateService = new TestRunStateService();
        runStateService.add(request(ExecutionModeEnum.DIRECT));
        runStateService.add(request(ExecutionModeEnum.WORKFLOW));
        ReflectionTestUtils.setField(dispatcher, "runStateService", runStateService);
        ReflectionTestUtils.setField(dispatcher, "runProperties", new GenerationRunProperties());

        GenerationRequest directRequest = runStateService.requests().get(0);
        GenerationRequest workflowRequest = runStateService.requests().get(1);
        when(direct.generate(any())).thenReturn(Flux.just(GenerationEvent.message("direct")));
        when(workflow.generate(any())).thenReturn(Flux.just(GenerationEvent.message("workflow")));

        List<GenerationEvent> directEvents = dispatcher.generate(directRequest).collectList().block();
        List<GenerationEvent> workflowEvents = dispatcher.generate(workflowRequest).collectList().block();
        assertEquals("run_started", directEvents.get(0).type());
        assertEquals("direct", ((Map<?, ?>) directEvents.get(1).data()).get("message"));
        assertEquals("done", directEvents.get(directEvents.size() - 1).type());
        assertEquals("workflow", ((Map<?, ?>) workflowEvents.get(1).data()).get("message"));
        assertEquals(1, directEvents.stream().filter(event -> "done".equals(event.type())).count());
        assertEquals(1, workflowEvents.stream().filter(event -> "done".equals(event.type())).count());
        assertRequiredRunFields(directEvents, directRequest);
        assertRequiredRunFields(workflowEvents, workflowRequest);
        verify(direct).generate(directRequest);
        verify(workflow).generate(workflowRequest);
    }

    @Test
    void cancellationStopsLaterEventsAndSendsOneCancelledAndDone() throws Exception {
        DirectGenerationStrategy direct = mock(DirectGenerationStrategy.class);
        WorkflowGenerationStrategy workflow = mock(WorkflowGenerationStrategy.class);
        GenerationDispatcher dispatcher = new GenerationDispatcher();
        ReflectionTestUtils.setField(dispatcher, "directGenerationStrategy", direct);
        ReflectionTestUtils.setField(dispatcher, "workflowGenerationStrategy", workflow);
        TestRunStateService runStateService = new TestRunStateService();
        GenerationRequest request = request(ExecutionModeEnum.DIRECT);
        runStateService.add(request);
        ReflectionTestUtils.setField(dispatcher, "runStateService", runStateService);
        ReflectionTestUtils.setField(dispatcher, "runProperties", new GenerationRunProperties());

        Sinks.Many<GenerationEvent> source = Sinks.many().unicast().onBackpressureBuffer();
        when(direct.generate(request)).thenReturn(source.asFlux());
        List<GenerationEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        dispatcher.generate(request).subscribe(events::add, ignored -> done.countDown(), done::countDown);

        waitForRunStarted(events);
        runStateService.cancel(request.runId());
        source.tryEmitNext(GenerationEvent.message("must not be forwarded"));
        source.tryEmitComplete();

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("run_started", "cancelled", "done"),
                events.stream().map(GenerationEvent::type).toList());
        assertEquals(GenerationRunStatus.CANCELLED, runStateService.getRequired(request.runId()).status());
    }

    @Test
    void directTimeoutProducesTimedOutErrorAndDone() {
        DirectGenerationStrategy direct = mock(DirectGenerationStrategy.class);
        WorkflowGenerationStrategy workflow = mock(WorkflowGenerationStrategy.class);
        GenerationDispatcher dispatcher = new GenerationDispatcher();
        ReflectionTestUtils.setField(dispatcher, "directGenerationStrategy", direct);
        ReflectionTestUtils.setField(dispatcher, "workflowGenerationStrategy", workflow);
        TestRunStateService runStateService = new TestRunStateService();
        GenerationRequest request = request(ExecutionModeEnum.DIRECT);
        runStateService.add(request);
        GenerationRunProperties properties = new GenerationRunProperties();
        properties.setDirectTimeout(Duration.ofMillis(20));
        ReflectionTestUtils.setField(dispatcher, "runStateService", runStateService);
        ReflectionTestUtils.setField(dispatcher, "runProperties", properties);
        when(direct.generate(request)).thenReturn(Flux.never());

        List<GenerationEvent> events = dispatcher.generate(request).collectList().block(Duration.ofSeconds(2));

        assertEquals(List.of("run_started", "generation_error", "done"),
                events.stream().map(GenerationEvent::type).toList());
        assertEquals(GenerationRunStatus.TIMED_OUT, runStateService.getRequired(request.runId()).status());
    }

    @Test
    void workflowTimeoutProducesTimedOutErrorAndDone() {
        DirectGenerationStrategy direct = mock(DirectGenerationStrategy.class);
        WorkflowGenerationStrategy workflow = mock(WorkflowGenerationStrategy.class);
        GenerationDispatcher dispatcher = new GenerationDispatcher();
        ReflectionTestUtils.setField(dispatcher, "directGenerationStrategy", direct);
        ReflectionTestUtils.setField(dispatcher, "workflowGenerationStrategy", workflow);
        TestRunStateService runStateService = new TestRunStateService();
        GenerationRequest request = request(ExecutionModeEnum.WORKFLOW);
        runStateService.add(request);
        GenerationRunProperties properties = new GenerationRunProperties();
        properties.setWorkflowTimeout(Duration.ofMillis(20));
        ReflectionTestUtils.setField(dispatcher, "runStateService", runStateService);
        ReflectionTestUtils.setField(dispatcher, "runProperties", properties);
        when(workflow.generate(request)).thenReturn(Flux.never());

        List<GenerationEvent> events = dispatcher.generate(request).collectList().block(Duration.ofSeconds(2));

        assertEquals(List.of("run_started", "generation_error", "done"),
                events.stream().map(GenerationEvent::type).toList());
        assertEquals(GenerationRunStatus.TIMED_OUT, runStateService.getRequired(request.runId()).status());
    }

    @Test
    void concurrentRunsKeepStateAndEventsSeparated() {
        DirectGenerationStrategy direct = mock(DirectGenerationStrategy.class);
        WorkflowGenerationStrategy workflow = mock(WorkflowGenerationStrategy.class);
        GenerationDispatcher dispatcher = new GenerationDispatcher();
        ReflectionTestUtils.setField(dispatcher, "directGenerationStrategy", direct);
        ReflectionTestUtils.setField(dispatcher, "workflowGenerationStrategy", workflow);
        TestRunStateService runStateService = new TestRunStateService();
        GenerationRequest directRequest = request(ExecutionModeEnum.DIRECT);
        GenerationRequest workflowRequest = request(ExecutionModeEnum.WORKFLOW);
        runStateService.add(directRequest);
        runStateService.add(workflowRequest);
        ReflectionTestUtils.setField(dispatcher, "runStateService", runStateService);
        ReflectionTestUtils.setField(dispatcher, "runProperties", new GenerationRunProperties());
        when(direct.generate(directRequest)).thenReturn(Flux.just(GenerationEvent.message("direct")));
        when(workflow.generate(workflowRequest)).thenReturn(Flux.just(GenerationEvent.message("workflow")));

        List<GenerationEvent> events = Flux.merge(
                        dispatcher.generate(directRequest), dispatcher.generate(workflowRequest))
                .collectList().block();

        assertTrue(events.stream().filter(event -> event.data() instanceof Map<?, ?>)
                .map(event -> String.valueOf(((Map<?, ?>) event.data()).get("runId")))
                .allMatch(runId -> runId.equals(directRequest.runId()) || runId.equals(workflowRequest.runId())));
        assertEquals(GenerationRunStatus.SUCCEEDED, runStateService.getRequired(directRequest.runId()).status());
        assertEquals(GenerationRunStatus.SUCCEEDED, runStateService.getRequired(workflowRequest.runId()).status());
    }

    @Test
    void replayUsesSequenceAndReportsExpiredWindowAfterMoreThan512Events() {
        DirectGenerationStrategy direct = mock(DirectGenerationStrategy.class);
        WorkflowGenerationStrategy workflow = mock(WorkflowGenerationStrategy.class);
        GenerationDispatcher dispatcher = new GenerationDispatcher();
        ReflectionTestUtils.setField(dispatcher, "directGenerationStrategy", direct);
        ReflectionTestUtils.setField(dispatcher, "workflowGenerationStrategy", workflow);
        TestRunStateService runStateService = new TestRunStateService();
        GenerationRequest request = request(ExecutionModeEnum.DIRECT);
        runStateService.add(request);
        ReflectionTestUtils.setField(dispatcher, "runStateService", runStateService);
        ReflectionTestUtils.setField(dispatcher, "runProperties", new GenerationRunProperties());
        when(direct.generate(request)).thenReturn(Flux.range(0, 600)
                .map(index -> GenerationEvent.message("message-" + index)));

        List<GenerationEvent> allEvents = dispatcher.generate(request).collectList().block(Duration.ofSeconds(2));
        assertEquals(602, allEvents.size());
        assertEquals(1L, allEvents.get(0).sequence());
        assertEquals(602L, allEvents.get(allEvents.size() - 1).sequence());

        List<GenerationEvent> resumed = dispatcher.subscribe(request.runId(), 1L)
                .collectList().block(Duration.ofSeconds(2));
        assertEquals("replay_reset", resumed.get(0).type());
        assertTrue(((Map<?, ?>) resumed.get(0).data()).containsKey("replayWindowExpired"));
        List<Long> replayedSequences = resumed.stream()
                .filter(event -> event.sequence() > 0)
                .map(GenerationEvent::sequence)
                .toList();
        assertEquals(91L, replayedSequences.get(1));
        assertEquals(602L, replayedSequences.get(replayedSequences.size() - 1));
        assertTrue(replayedSequences.stream().distinct().count() == replayedSequences.size());
    }

    private void assertRequiredRunFields(List<GenerationEvent> events, GenerationRequest request) {
        for (GenerationEvent event : events) {
            assertTrue(event.data() instanceof Map<?, ?>);
            Map<?, ?> data = (Map<?, ?>) event.data();
            assertEquals(request.runId(), data.get("runId"));
            assertEquals(String.valueOf(request.appId()), data.get("appId"));
            assertEquals(request.executionMode().name(), data.get("executionMode"));
            assertTrue(data.containsKey("status"));
            assertTrue(data.containsKey("currentStep"));
            assertTrue(data.containsKey("message"));
            assertTrue(data.get("sequence") instanceof Number);
        }
    }

    private void waitForRunStarted(List<GenerationEvent> events) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (events.stream().noneMatch(event -> "run_started".equals(event.type()))
                && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(events.stream().anyMatch(event -> "run_started".equals(event.type())));
    }

    private GenerationRequest request(ExecutionModeEnum mode) {
        return new GenerationRequest(1L, 2L, "hello", CodeGenTypeEnum.HTML, mode, null,
                java.util.UUID.randomUUID().toString());
    }

    private static class TestRunStateService extends GenerationRunStateService {
        private final Map<String, GenerationRunState> states = new ConcurrentHashMap<>();

        void add(GenerationRequest request) {
            states.put(request.runId(), new GenerationRunState(request.runId(), request.appId(), request.userId(),
                    request.executionMode(), GenerationRunStatus.PENDING, "排队中", "now", "now", null, null,
                    0, 2, false));
        }

        List<GenerationRequest> requests() {
            return states.values().stream()
                    .sorted(Comparator.comparing(state -> state.executionMode().name()))
                    .map(state -> new GenerationRequest(state.appId(), state.userId(), "hello", CodeGenTypeEnum.HTML,
                            state.executionMode(), null, state.runId()))
                    .toList();
        }

        @Override
        public boolean transitionToRunning(String runId, String currentStep) {
            GenerationRunState old = states.get(runId);
            states.put(runId, new GenerationRunState(old.runId(), old.appId(), old.userId(), old.executionMode(),
                    GenerationRunStatus.RUNNING, currentStep, old.startedAt(), "now", old.finishedAt(),
                    old.errorMessage(), old.retryCount(), old.maxRetryCount(), old.cancelRequested()));
            return true;
        }

        @Override
        public boolean updateCurrentStep(String runId, String currentStep) {
            GenerationRunState old = states.get(runId);
            states.put(runId, new GenerationRunState(old.runId(), old.appId(), old.userId(), old.executionMode(),
                    old.status(), currentStep, old.startedAt(), "now", old.finishedAt(), old.errorMessage(),
                    old.retryCount(), old.maxRetryCount(), old.cancelRequested()));
            return true;
        }

        @Override
        public boolean isCancellationRequested(String runId) {
            return states.get(runId).cancelRequested();
        }

        @Override
        public boolean finish(String runId, GenerationRunStatus desiredStatus, String errorMessage) {
            GenerationRunState old = states.get(runId);
            states.put(runId, new GenerationRunState(old.runId(), old.appId(), old.userId(), old.executionMode(),
                    desiredStatus, old.currentStep(), old.startedAt(), "now", "now", errorMessage,
                    old.retryCount(), old.maxRetryCount(), old.cancelRequested()));
            return true;
        }

        @Override
        public GenerationRunState getRequired(String runId) {
            return states.get(runId);
        }

        @Override
        public Optional<GenerationRunState> find(String runId) {
            return Optional.ofNullable(states.get(runId));
        }

        void cancel(String runId) {
            GenerationRunState old = states.get(runId);
            states.put(runId, new GenerationRunState(old.runId(), old.appId(), old.userId(), old.executionMode(),
                    old.status(), old.currentStep(), "now", "now", old.finishedAt(), old.errorMessage(),
                    old.retryCount(), old.maxRetryCount(), true));
        }
    }
}
