package com.swu.aiZeroCodeHub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swu.aiZeroCodeHub.core.ratelimit.DistributedRateLimiter;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.generation.GenerationEvent;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import com.swu.aiZeroCodeHub.service.AppService;
import com.swu.aiZeroCodeHub.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void controllerUsesDefaultDirectModeAndAddsOneDoneEvent() {
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

        assertEquals(2, events.size());
        assertEquals("message", events.get(0).event());
        assertEquals("done", events.get(1).event());
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
        assertEquals("error", events.get(0).event());
        assertTrue(events.get(0).data().contains("用户消息不能超过8000个字符"));
        assertEquals("done", events.get(1).event());
    }
}
