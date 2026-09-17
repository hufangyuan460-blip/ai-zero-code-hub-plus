package com.swu.aiZeroCodeHub.langgraph4j;

import com.swu.aiZeroCodeHub.generation.GenerationEvent;
import com.swu.aiZeroCodeHub.langgraph4j.model.QualityResult;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeGenWorkflowRetryTest {

    @Test
    void qualityFailureStopsAfterTwoRepairAttempts() {
        WorkflowContext context = WorkflowContext.builder()
                .appId(99L)
                .repairAttempt(0)
                .maxRepairAttempts(2)
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

    @SuppressWarnings("unchecked")
    private MessagesState<String> stateWith(WorkflowContext context) {
        MessagesState<String> state = mock(MessagesState.class);
        when(state.data()).thenReturn((Map) Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, context));
        return state;
    }

    private String route(CodeGenWorkflow workflow, MessagesState<String> state) {
        return ReflectionTestUtils.invokeMethod(workflow, "routeAfterQualityCheck", state);
    }

    private QualityResult failedResult() {
        return QualityResult.builder()
                .isValid(false)
                .errors(List.of("存在质量问题"))
                .suggestions(List.of("请修复"))
                .build();
    }
}
