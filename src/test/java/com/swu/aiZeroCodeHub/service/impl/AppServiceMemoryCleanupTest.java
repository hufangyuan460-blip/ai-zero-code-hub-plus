package com.swu.aiZeroCodeHub.service.impl;

import com.swu.aiZeroCodeHub.config.AiCodeGeneratorServiceFactory;
import com.swu.aiZeroCodeHub.model.entity.App;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class AppServiceMemoryCleanupTest {

    @Test
    void deletingAppRemovesDatabaseHistoryRedisMemoryAndAiServiceCache() {
        long appId = 10001L;
        App app = new App();
        app.setId(appId);
        app.setUserId(10002L);

        AppServiceImpl appService = spy(new AppServiceImpl());
        ChatHistoryService historyService = mock(ChatHistoryService.class);
        RedisChatMemoryStore redisStore = mock(RedisChatMemoryStore.class);
        AiCodeGeneratorServiceFactory factory = mock(AiCodeGeneratorServiceFactory.class);
        ReflectionTestUtils.setField(appService, "chatHistoryService", historyService);
        ReflectionTestUtils.setField(appService, "redisChatMemoryStore", redisStore);
        ReflectionTestUtils.setField(appService, "aiCodeGeneratorServiceFactory", factory);
        doReturn(app).when(appService).getById(appId);
        doReturn(true).when(appService).removeById(appId);

        assertTrue(appService.adminDeleteApp(appId));

        verify(historyService).deleteChatHistoryByAppId(appId);
        verify(redisStore).deleteMessages(appId);
        verify(factory).invalidateAppCache(appId);
    }
}
