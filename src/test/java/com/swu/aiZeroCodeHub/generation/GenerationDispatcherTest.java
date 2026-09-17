package com.swu.aiZeroCodeHub.generation;

import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        GenerationRequest directRequest = request(ExecutionModeEnum.DIRECT);
        GenerationRequest workflowRequest = request(ExecutionModeEnum.WORKFLOW);
        when(direct.generate(any())).thenReturn(Flux.just(GenerationEvent.message("direct")));
        when(workflow.generate(any())).thenReturn(Flux.just(GenerationEvent.message("workflow")));

        assertEquals("direct", dispatcher.generate(directRequest).blockFirst().data());
        assertEquals("workflow", dispatcher.generate(workflowRequest).blockFirst().data());
        verify(direct).generate(directRequest);
        verify(workflow).generate(workflowRequest);
    }

    private GenerationRequest request(ExecutionModeEnum mode) {
        return new GenerationRequest(1L, 2L, "hello", CodeGenTypeEnum.HTML, mode, null, "request");
    }
}
