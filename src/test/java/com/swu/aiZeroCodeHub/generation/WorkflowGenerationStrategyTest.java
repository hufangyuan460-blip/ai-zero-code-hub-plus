package com.swu.aiZeroCodeHub.generation;

import com.swu.aiZeroCodeHub.langgraph4j.CodeGenWorkflow;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryAddRequest;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.enums.ChatHistoryMessageTypeEnum;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowGenerationStrategyTest {

    @Test
    @SuppressWarnings("unchecked")
    void workflowPersistsOneFinalAiHistoryAfterStructuredEvents() {
        CodeGenWorkflow workflow = mock(CodeGenWorkflow.class);
        ChatHistoryService historyService = mock(ChatHistoryService.class);
        WorkflowGenerationStrategy strategy = new WorkflowGenerationStrategy();
        ReflectionTestUtils.setField(strategy, "codeGenWorkflow", workflow);
        ReflectionTestUtils.setField(strategy, "chatHistoryService", historyService);

        User user = new User();
        user.setId(22L);
        GenerationRequest request = new GenerationRequest(
                11L, 22L, "生成页面", CodeGenTypeEnum.HTML,
                ExecutionModeEnum.WORKFLOW, user, "request-1");
        doAnswer(invocation -> {
            Consumer<WorkflowContext> callback = invocation.getArgument(1);
            callback.accept(WorkflowContext.builder()
                    .appId(11L)
                    .aiHistoryContent("最终 AI 回复")
                    .build());
            return Flux.just(
                    GenerationEvent.workflowStart("start"),
                    GenerationEvent.message("message"),
                    GenerationEvent.workflowCompleted("complete"));
        }).when(workflow).executeWorkflowWithFlux(any(GenerationRequest.class), any(Consumer.class));

        assertEquals(3, strategy.generate(request).collectList().block().size());

        var captor = org.mockito.ArgumentCaptor.forClass(ChatHistoryAddRequest.class);
        verify(historyService).addChatHistory(captor.capture(), org.mockito.ArgumentMatchers.same(user));
        assertEquals(ChatHistoryMessageTypeEnum.AI.getValue(), captor.getValue().getMessageType());
        assertEquals("最终 AI 回复", captor.getValue().getContent());
    }
}
