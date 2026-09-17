package com.swu.aiZeroCodeHub.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCodeGeneratorServiceFactoryTest {

    private static final long APP_ID = 7001L;

    @Test
    void rebuiltMemoryExposesAtMostTwentyMessagesAndLoadsTwentyHistoryRows() {
        RedisChatMemoryStore redisStore = mock(RedisChatMemoryStore.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        List<ChatMessage> cachedMessages = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            cachedMessages.add(UserMessage.from("message-" + i));
        }
        when(redisStore.getMessages(APP_ID)).thenReturn(cachedMessages);

        AiCodeGeneratorServiceFactory factory = new AiCodeGeneratorServiceFactory();
        ReflectionTestUtils.setField(factory, "redisChatMemoryStore", redisStore);
        ReflectionTestUtils.setField(factory, "chatHistoryService", chatHistoryService);

        MessageWindowChatMemory memory = ReflectionTestUtils.invokeMethod(factory, "createChatMemory", APP_ID);

        assertEquals(20, memory.messages().size());
        verify(chatHistoryService).loadChatHistoryToMemory(APP_ID, memory, 20);
    }
}
