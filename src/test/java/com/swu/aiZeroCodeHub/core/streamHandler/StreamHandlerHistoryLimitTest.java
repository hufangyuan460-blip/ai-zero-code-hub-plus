package com.swu.aiZeroCodeHub.core.streamHandler;

import cn.hutool.json.JSONUtil;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryAddRequest;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class StreamHandlerHistoryLimitTest {

    @Test
    void simpleTextHandlerPersistsPlaceholderForOversizedAiOutput() {
        ChatHistoryService historyService = mock(ChatHistoryService.class);
        SimpleTextStreamHandler handler = new SimpleTextStreamHandler();

        handler.handle(Flux.just("x".repeat(ChatHistoryService.MAX_AI_HISTORY_LENGTH + 1)),
                historyService, 8001L, user()).blockLast();

        ArgumentCaptor<ChatHistoryAddRequest> captor = ArgumentCaptor.forClass(ChatHistoryAddRequest.class);
        verify(historyService).addChatHistory(captor.capture(), any(User.class));
        assertEquals(ChatHistoryService.OVERSIZED_AI_HISTORY_PLACEHOLDER, captor.getValue().getContent());
    }

    @Test
    void jsonHandlerPersistsOnlyVueToolSummary() {
        ChatHistoryService historyService = mock(ChatHistoryService.class);
        JsonMessageStreamHandler handler = new JsonMessageStreamHandler();
        ToolManagerStub toolManager = new ToolManagerStub();
        ReflectionTestUtils.setField(handler, "sseParser", new SSEParser());
        ReflectionTestUtils.setField(handler, "toolManager", toolManager.toolManager());

        String arguments = JSONUtil.toJsonStr(Map.of(
                "relativeFilePath", "src/App.vue",
                "content", "FULL_SOURCE_CONTENT",
                "oldContent", "OLD_SOURCE_CONTENT",
                "newContent", "NEW_SOURCE_CONTENT"));
        String event = JSONUtil.toJsonStr(Map.of(
                "type", "tool_executed",
                "name", "modifyFile",
                "arguments", arguments,
                "result", "ok"));

        handler.handle(Flux.just("data: " + event), historyService, 8002L, user()).blockLast();

        ArgumentCaptor<ChatHistoryAddRequest> captor = ArgumentCaptor.forClass(ChatHistoryAddRequest.class);
        verify(historyService).addChatHistory(captor.capture(), any(User.class));
        String history = captor.getValue().getContent();
        assertTrue(history.contains("已修改文件：src/App.vue"));
        assertTrue(!history.contains("FULL_SOURCE_CONTENT"));
        assertTrue(!history.contains("OLD_SOURCE_CONTENT"));
        assertTrue(!history.contains("NEW_SOURCE_CONTENT"));
    }

    private User user() {
        User user = new User();
        user.setId(9001L);
        user.setUserRole("user");
        return user;
    }

    private static class ToolManagerStub {
        private com.swu.aiZeroCodeHub.manager.ToolManager toolManager() {
            return mock(com.swu.aiZeroCodeHub.manager.ToolManager.class);
        }
    }
}
