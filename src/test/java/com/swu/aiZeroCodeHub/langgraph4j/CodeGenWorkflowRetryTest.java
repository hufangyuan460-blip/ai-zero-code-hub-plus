package com.swu.aiZeroCodeHub.langgraph4j;

import com.swu.aiZeroCodeHub.generation.GenerationEvent;
import com.swu.aiZeroCodeHub.generation.GenerationCancelledException;
import com.swu.aiZeroCodeHub.langgraph4j.model.QualityResult;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import com.swu.aiZeroCodeHub.service.GenerationRunStateService;
import com.swu.aiZeroCodeHub.validation.ValidationIssue;
import com.swu.aiZeroCodeHub.validation.ValidationReport;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class CodeGenWorkflowRetryTest {

    @Test
    void qualityFailureStopsAfterTwoRepairAttempts() {
        WorkflowContext context = WorkflowContext.builder()
                .appId(99L)
                .repairAttempt(0)
                .retryCount(0)
                .maxRepairAttempts(2)
                .maxRetryCount(2)
                .qualityFailureRepairable(true)
                .qualityResult(failedResult())
                .build();
        MessagesState<String> state = stateWith(context);
        CodeGenWorkflow workflow = new CodeGenWorkflow();

        assertEquals("retry", route(workflow, state));
        assertEquals(1, context.getRepairAttempt());
        assertEquals("retry", route(workflow, state));
        assertEquals(2, context.getRepairAttempt());
        assertEquals("failed", route(workflow, state));
        assertTrue(context.getErrorMessage().contains("代码质检阶段失败"));
    }

    @Test
    void nonVueGenerationExplicitlySkipsBuildStep() {
        List<GenerationEvent> events = new ArrayList<>();
        WorkflowContext context = WorkflowContext.builder()
                .generationType(com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum.HTML)
                .qualityResult(QualityResult.builder().isValid(true).build())
                .eventPublisher(events::add)
                .build();

        assertEquals("skip_build", route(new CodeGenWorkflow(), stateWith(context)));
        assertEquals("step_started", events.get(0).type());
        assertEquals("step_completed", events.get(1).type());
        assertTrue(String.valueOf(events.get(1).data()).contains("已跳过"));
    }

    @Test
    void cancellationPreventsQualityRetry() {
        WorkflowContext context = WorkflowContext.builder()
                .retryCount(0)
                .maxRetryCount(2)
                .maxRepairAttempts(2)
                .qualityFailureRepairable(true)
                .qualityResult(failedResult())
                .cancellationChecker(() -> true)
                .build();

        assertThrows(GenerationCancelledException.class,
                () -> route(new CodeGenWorkflow(), stateWith(context)));
        assertEquals(0, context.getRetryCount());
    }

    @Test
    void repairableVueBuildFailureUsesOneDirectedRepair() {
        GenerationRunStateService stateService = mock(GenerationRunStateService.class);
        when(stateService.registerRepair("run-1", "build-fingerprint", "build-hash",
                List.of("package.json"), 1, "项目构建定向修复")).thenReturn(1);
        CodeGenWorkflow workflow = new CodeGenWorkflow();
        ReflectionTestUtils.setField(workflow, "runStateService", stateService);
        WorkflowContext context = WorkflowContext.builder()
                .runId("run-1")
                .generationType(CodeGenTypeEnum.VUE_PROJECT)
                .repairAttempt(0)
                .retryCount(0)
                .maxRepairAttempts(1)
                .maxRetryCount(2)
                .validationReport(buildFailure(true))
                .build();

        assertEquals("retry", routeBuild(workflow, stateWith(context)));
        assertEquals(1, context.getRepairAttempt());
        assertEquals(1, context.getRetryCount());
        verify(stateService).registerRepair("run-1", "build-fingerprint", "build-hash",
                List.of("package.json"), 1, "项目构建定向修复");
    }

    @Test
    void nonRepairableBuildFailureStopsSafely() {
        WorkflowContext context = WorkflowContext.builder()
                .generationType(CodeGenTypeEnum.VUE_PROJECT)
                .repairAttempt(0)
                .maxRepairAttempts(1)
                .validationReport(buildFailure(false))
                .build();

        assertEquals("failed", routeBuild(new CodeGenWorkflow(), stateWith(context)));
        assertTrue(context.getErrorMessage().contains("构建阶段失败"));
    }

    @Test
    void duplicateOrUnchangedBuildRepairStopsWithoutAnotherAttempt() {
        for (int result : List.of(-4, -5, -6)) {
            GenerationRunStateService stateService = mock(GenerationRunStateService.class);
            when(stateService.registerRepair("run-2", "build-fingerprint", "build-hash",
                    List.of("package.json"), 1, "项目构建定向修复")).thenReturn(result);
            CodeGenWorkflow workflow = new CodeGenWorkflow();
            ReflectionTestUtils.setField(workflow, "runStateService", stateService);
            WorkflowContext context = WorkflowContext.builder()
                    .runId("run-2")
                    .repairAttempt(0)
                    .maxRepairAttempts(1)
                    .validationReport(buildFailure(true))
                    .build();

            assertEquals("failed", routeBuild(workflow, stateWith(context)));
            assertEquals(0, context.getRepairAttempt());
        }
    }

    @Test
    void successfulVueBuildDoesNotEnterRepair() {
        WorkflowContext context = WorkflowContext.builder()
                .generationType(CodeGenTypeEnum.VUE_PROJECT)
                .validationReport(new ValidationReport(true, List.of(), "通过", "ok", false,
                        List.of("package.json"), "hash", null))
                .build();

        assertEquals("completed", routeBuild(new CodeGenWorkflow(), stateWith(context)));
    }

    @SuppressWarnings("unchecked")
    private MessagesState<String> stateWith(WorkflowContext context) {
        MessagesState<String> state = mock(MessagesState.class);
        when(state.data()).thenReturn((Map) Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, context));
        return state;
    }

    private String route(CodeGenWorkflow workflow, MessagesState<String> state) {
        return ReflectionTestUtils.invokeMethod(workflow, "routeAfterQualityCheck", state);
    }

    private String routeBuild(CodeGenWorkflow workflow, MessagesState<String> state) {
        return ReflectionTestUtils.invokeMethod(workflow, "routeAfterProjectBuild", state);
    }

    private QualityResult failedResult() {
        return QualityResult.builder()
                .isValid(false)
                .errors(List.of("存在质量问题"))
                .suggestions(List.of("请修复"))
                .build();
    }

    private ValidationReport buildFailure(boolean repairable) {
        return new ValidationReport(false,
                List.of(new ValidationIssue("BUILD_FAILED", "构建命令返回失败", "package.json", repairable)),
                "构建命令返回失败", "build-fingerprint", repairable,
                List.of("package.json"), "build-hash", "构建失败摘要");
    }
}
