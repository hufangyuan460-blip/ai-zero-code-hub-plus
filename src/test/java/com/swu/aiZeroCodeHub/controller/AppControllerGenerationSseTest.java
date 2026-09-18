package com.swu.aiZeroCodeHub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swu.aiZeroCodeHub.core.ratelimit.DistributedRateLimiter;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.generation.GenerationEvent;
import com.swu.aiZeroCodeHub.model.dto.generation.GenerationCreateRequest;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.vo.generation.GenerationCreateVO;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import com.swu.aiZeroCodeHub.service.AppService;
import com.swu.aiZeroCodeHub.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentCaptor.forClass;

class AppControllerGenerationSseTest {

    @Test
    void controllerAddsSseMetadataWithoutNestedSseText() {
        AppController controller = new AppController();
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());

        ServerSentEvent<?> event = ReflectionTestUtils.invokeMethod(
                controller, "toSseEvent", GenerationEvent.message("plain message"));

        assertEquals("message", event.event());
        assertEquals("plain message", event.data());
        assertFalse(String.valueOf(event.data()).contains("event: message"));
    }

    @Test
    void controllerMapsRunSequenceToSseId() {
        AppController controller = new AppController();
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());

        ServerSentEvent<?> event = ReflectionTestUtils.invokeMethod(
                controller, "toSseEvent", new GenerationEvent("message", Map.of("runId", "run-1"), 17L));

        assertEquals("17", event.id());
        assertEquals("message", event.event());
    }

    @Test
    void controllerUsesDefaultDirectModeAndMapsReturnedEvents() {
        AppController controller = new AppController();
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        DistributedRateLimiter rateLimiter = mock(DistributedRateLimiter.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        User user = new User();
        user.setId(2L);
        ReflectionTestUtils.setField(controller, "appService", appService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "distributedRateLimiter", rateLimiter);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        when(userService.getLoginUser(request)).thenReturn(user);
        when(rateLimiter.tryAcquire(any(), eq(1L), eq(5L))).thenReturn(true);
        when(appService.chatToGenCode(1L, "hello", "html", null, user))
                .thenReturn(Flux.just(GenerationEvent.message("hello")));

        List<ServerSentEvent<String>> events = controller
                .chatGenerateCode(1L, "hello", "html", null, request)
                .collectList()
                .block();

        assertEquals(1, events.size());
        assertEquals("message", events.get(0).event());
    }

    @Test
    void controllerReturnsOversizedMessageAsSseErrorWithoutWorkflowEvents() {
        AppController controller = new AppController();
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        DistributedRateLimiter rateLimiter = mock(DistributedRateLimiter.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        User user = new User();
        user.setId(2L);
        String oversizedMessage = "x".repeat(ChatHistoryService.MAX_USER_MESSAGE_LENGTH + 1);

        ReflectionTestUtils.setField(controller, "appService", appService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "distributedRateLimiter", rateLimiter);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        when(userService.getLoginUser(request)).thenReturn(user);
        when(rateLimiter.tryAcquire(any(), eq(1L), eq(5L))).thenReturn(true);
        when(appService.chatToGenCode(1L, oversizedMessage, "html", "WORKFLOW", user))
                .thenThrow(new BusinessException(ErrorCode.PARAM_ERROR, "用户消息不能超过8000个字符"));

        List<ServerSentEvent<String>> events = controller
                .chatGenerateCode(1L, oversizedMessage, "html", "WORKFLOW", request)
                .collectList()
                .block();

        assertEquals(2, events.size());
        assertEquals("generation_error", events.get(0).event());
        assertTrue(events.get(0).data().contains("用户消息不能超过8000个字符"));
        assertEquals("done", events.get(1).event());
    }

    @Test
    void newGenerationEndpointCreatesRunWithoutPuttingPromptInTheStreamUrl() {
        AppController controller = new AppController();
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        User user = new User();
        user.setId(2L);
        GenerationCreateRequest body = new GenerationCreateRequest();
        body.setAppId(1L);
        body.setUserMessage("a prompt that stays in the JSON body");
        body.setCodeGenType("html");
        body.setExecutionMode("DIRECT");
        ReflectionTestUtils.setField(controller, "appService", appService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        when(userService.getLoginUser(request)).thenReturn(user);
        when(appService.createGeneration(body, user)).thenReturn(
                new GenerationCreateVO("6f2e3b6a-8a30-4ae0-9f09-90ab5a6cd0f0", "PENDING"));

        var response = controller.createGeneration(body, request);

        assertEquals("6f2e3b6a-8a30-4ae0-9f09-90ab5a6cd0f0", response.getData().runId());
        verify(appService).createGeneration(body, user);
    }

    @Test
    void getAndPostGenerationShareUserRateLimitBeforeAnyGenerationSideEffect() {
        AppController controller = new AppController();
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        DistributedRateLimiter rateLimiter = mock(DistributedRateLimiter.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        User user = new User();
        user.setId(2L);
        GenerationCreateRequest body = new GenerationCreateRequest();
        body.setAppId(1L);
        body.setUserMessage("prompt");
        body.setCodeGenType("html");
        body.setExecutionMode("DIRECT");
        ReflectionTestUtils.setField(controller, "appService", appService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "distributedRateLimiter", rateLimiter);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        when(userService.getLoginUser(request)).thenReturn(user);
        when(rateLimiter.tryAcquire(any(), eq(1L), eq(5L))).thenReturn(false);

        assertThrows(com.swu.aiZeroCodeHub.core.ratelimit.RateLimitException.class,
                () -> controller.createGeneration(body, request));
        controller.chatGenerateCode(1L, "prompt", "html", "DIRECT", request).collectList().block();

        var keyCaptor = forClass(String.class);
        verify(rateLimiter, org.mockito.Mockito.times(2)).tryAcquire(keyCaptor.capture(), eq(1L), eq(5L));
        assertEquals(keyCaptor.getAllValues().get(0), keyCaptor.getAllValues().get(1));
        verify(appService, never()).createGeneration(any(), eq(user));
        verify(appService, never()).chatToGenCode(any(), any(), any(), any(), eq(user));
    }

    @Test
    void streamEndpointMapsRunIdPayloadAsSseData() {
        AppController controller = new AppController();
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        User user = new User();
        user.setId(2L);
        ReflectionTestUtils.setField(controller, "appService", appService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        when(userService.getLoginUser(request)).thenReturn(user);
        when(appService.subscribeGeneration("6f2e3b6a-8a30-4ae0-9f09-90ab5a6cd0f0", user))
                .thenReturn(Flux.just(GenerationEvent.runStarted(Map.of(
                        "runId", "6f2e3b6a-8a30-4ae0-9f09-90ab5a6cd0f0",
                        "status", "RUNNING"))));

        List<ServerSentEvent<String>> events = controller
                .streamGeneration("6f2e3b6a-8a30-4ae0-9f09-90ab5a6cd0f0", request)
                .collectList().block();

        assertEquals("run_started", events.get(0).event());
        assertTrue(events.get(0).data().contains("6f2e3b6a-8a30-4ae0-9f09-90ab5a6cd0f0"));
        assertFalse(events.get(0).data().contains("event:"));
    }
}
