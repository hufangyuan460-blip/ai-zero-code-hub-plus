package com.swu.aiZeroCodeHub.service.impl;

import com.swu.aiZeroCodeHub.generation.GenerationDispatcher;
import com.swu.aiZeroCodeHub.generation.GenerationEvent;
import com.swu.aiZeroCodeHub.generation.GenerationRequest;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryAddRequest;
import com.swu.aiZeroCodeHub.model.entity.App;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import com.swu.aiZeroCodeHub.service.GenerationRunStateService;
import com.swu.aiZeroCodeHub.core.builder.VueProjectBuilder;
import com.swu.aiZeroCodeHub.core.build.ProjectBuildExecutor;
import com.swu.aiZeroCodeHub.generation.GenerationRunProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppServiceGenerationModeTest {

    @Test
    void missingModeUsesDirectAndEachRequestCanSelectWorkflow() {
        AppServiceImpl appService = mockService();
        User owner = owner(2L);
        App app = app(1L, owner.getId());
        doReturn(app).when(appService).getById(1L);
        when(dispatcher(appService).generate(any())).thenReturn(Flux.just(GenerationEvent.message("ok")));

        appService.chatToGenCode(1L, "hello", "html", null, owner).blockLast();
        appService.chatToGenCode(1L, "hello", "html", "WORKFLOW", owner).blockLast();

        var captor = org.mockito.ArgumentCaptor.forClass(GenerationRequest.class);
        verify(dispatcher(appService), org.mockito.Mockito.times(2)).generate(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum.DIRECT,
                captor.getAllValues().get(0).executionMode());
        org.junit.jupiter.api.Assertions.assertEquals(com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum.WORKFLOW,
                captor.getAllValues().get(1).executionMode());
        assertNotEquals(captor.getAllValues().get(0).runId(), captor.getAllValues().get(1).runId());
        verify(runState(appService), times(2)).create(any(GenerationRequest.class), eq(2));
    }

    @Test
    void anotherUserCannotStartEitherGenerationMode() {
        AppServiceImpl appService = mockService();
        User owner = owner(2L);
        User other = owner(3L);
        doReturn(app(1L, owner.getId())).when(appService).getById(1L);

        assertThrows(RuntimeException.class,
                () -> appService.chatToGenCode(1L, "hello", "html", "DIRECT", other));
        assertThrows(RuntimeException.class,
                () -> appService.chatToGenCode(1L, "hello", "html", "WORKFLOW", other));
        verify(dispatcher(appService), never()).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECT", "WORKFLOW"})
    void vueGenerationIsRejectedBeforeTaskCreationWhenBuildCapabilityIsMissing(String executionMode) {
        AppServiceImpl appService = mockService();
        User owner = owner(2L);
        App vueApp = app(1L, owner.getId());
        vueApp.setCodeGenType("vue_project");
        doReturn(vueApp).when(appService).getById(1L);
        ReflectionTestUtils.setField(appService, "vueProjectBuilder",
                new VueProjectBuilder(new ProjectBuildExecutor() {
                    @Override
                    public com.swu.aiZeroCodeHub.core.build.BuildExecutionResult build(
                            com.swu.aiZeroCodeHub.core.build.BuildRequest request) {
                        return new com.swu.aiZeroCodeHub.core.build.BuildExecutionResult(false, "not used", -1, false,
                                "SANDBOX_NOT_CONFIGURED");
                    }

                    @Override
                    public boolean isAvailable() {
                        return false;
                    }
                }));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> appService.chatToGenCode(1L, "hello", "vue_project", executionMode, owner));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("构建能力未配置"));
        verify(dispatcher(appService), never()).generate(any());
        verify(history(appService), never()).addChatHistory(any(ChatHistoryAddRequest.class), any(User.class));
        verify(runState(appService), never()).create(any(GenerationRequest.class), any(Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECT", "WORKFLOW"})
    void messageAtMaximumLengthIsAcceptedForBothModes(String executionMode) {
        AppServiceImpl appService = mockService();
        User owner = owner(2L);
        doReturn(app(1L, owner.getId())).when(appService).getById(1L);
        when(dispatcher(appService).generate(any())).thenReturn(Flux.just(GenerationEvent.message("ok")));

        appService.chatToGenCode(1L,
                "x".repeat(ChatHistoryService.MAX_USER_MESSAGE_LENGTH),
                "html", executionMode, owner).blockLast();

        verify(dispatcher(appService), times(1)).generate(any());
        verify(history(appService), times(1)).addChatHistory(any(ChatHistoryAddRequest.class), eq(owner));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECT", "WORKFLOW"})
    void messageOverMaximumLengthIsRejectedBeforeAnyGenerationOrHistory(String executionMode) {
        AppServiceImpl appService = mockService();
        User owner = owner(2L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> appService.chatToGenCode(1L,
                        "x".repeat(ChatHistoryService.MAX_USER_MESSAGE_LENGTH + 1),
                        "html", executionMode, owner));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertEquals("用户消息不能超过8000个字符", exception.getMessage());
        verify(dispatcher(appService), never()).generate(any());
        verify(history(appService), never()).addChatHistory(any(ChatHistoryAddRequest.class), any(User.class));
        verify(appService, never()).getById(1L);
    }

    @Test
    void missingTypeAndEmptyAppAreRejectedBeforeAutomaticRoutingCosts() {
        AppServiceImpl appService = mockService();
        User owner = owner(2L);
        App emptyTypeApp = app(1L, owner.getId());
        emptyTypeApp.setCodeGenType(null);
        doReturn(emptyTypeApp).when(appService).getById(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> appService.chatToGenCode(1L, "hello", null, "WORKFLOW", owner));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("codeGenType 必须明确指定"));
        verify(dispatcher(appService), never()).generate(any());
        verify(history(appService), never()).addChatHistory(any(ChatHistoryAddRequest.class), any(User.class));
        verify(runState(appService), never()).create(any(GenerationRequest.class), any(Integer.class));
    }

    @Test
    void cancelGenerationMapsRequestedAndTerminalRunStates() {
        AppServiceImpl appService = mockService();
        User owner = owner(2L);
        GenerationRunStateService stateService = runState(appService);
        when(stateService.requestCancel("run-1", owner))
                .thenReturn(GenerationRunStateService.CancelResult.REQUESTED);
        when(stateService.requestCancel("run-2", owner))
                .thenReturn(GenerationRunStateService.CancelResult.NOT_ACTIVE);

        assertTrue(appService.cancelGeneration("run-1", owner));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> appService.cancelGeneration("run-2", owner));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), exception.getCode());
    }

    private AppServiceImpl mockService() {
        AppServiceImpl appService = mock(AppServiceImpl.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(appService, "chatHistoryService", mock(ChatHistoryService.class));
        ReflectionTestUtils.setField(appService, "generationDispatcher", mock(GenerationDispatcher.class));
        ReflectionTestUtils.setField(appService, "generationRunStateService", mock(GenerationRunStateService.class));
        ReflectionTestUtils.setField(appService, "generationRunProperties", new GenerationRunProperties());
        return appService;
    }

    private GenerationDispatcher dispatcher(AppServiceImpl appService) {
        return (GenerationDispatcher) ReflectionTestUtils.getField(appService, "generationDispatcher");
    }

    private ChatHistoryService history(AppServiceImpl appService) {
        return (ChatHistoryService) ReflectionTestUtils.getField(appService, "chatHistoryService");
    }

    private GenerationRunStateService runState(AppServiceImpl appService) {
        return (GenerationRunStateService) ReflectionTestUtils.getField(appService, "generationRunStateService");
    }

    private App app(long appId, long userId) {
        App app = new App();
        app.setId(appId);
        app.setUserId(userId);
        app.setCodeGenType("html");
        return app;
    }

    private User owner(long id) {
        User user = new User();
        user.setId(id);
        user.setUserRole("user");
        return user;
    }
}
