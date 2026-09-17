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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private AppServiceImpl mockService() {
        AppServiceImpl appService = mock(AppServiceImpl.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(appService, "chatHistoryService", mock(ChatHistoryService.class));
        ReflectionTestUtils.setField(appService, "generationDispatcher", mock(GenerationDispatcher.class));
        return appService;
    }

    private GenerationDispatcher dispatcher(AppServiceImpl appService) {
        return (GenerationDispatcher) ReflectionTestUtils.getField(appService, "generationDispatcher");
    }

    private ChatHistoryService history(AppServiceImpl appService) {
        return (ChatHistoryService) ReflectionTestUtils.getField(appService, "chatHistoryService");
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
