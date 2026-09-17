package com.swu.aiZeroCodeHub.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.swu.aiZeroCodeHub.aiService.AiCodeGeneratorService;
import com.swu.aiZeroCodeHub.aiService.AiVueCreateService;
import com.swu.aiZeroCodeHub.aiService.AiVueModifyService;
import com.swu.aiZeroCodeHub.manager.ToolManager;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;



@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {
    private static final int MAX_CHAT_MEMORY_MESSAGES = 20;

    @Autowired
    @Qualifier("openAiChatModelPrototype")
    private ObjectProvider<ChatModel> chatModelProvider;
    @Resource
    private StreamingChatModel openAiStreamingChatModel;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private StreamingChatModel reasoningStreamingChatModel;
    @Resource
    private ToolManager toolManager;


    /**
     * 根据生成类型创建AiCodeGeneratorService实例
     * @param appId
     * @param codeGenType
     * @return
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        MessageWindowChatMemory chatMemory = createChatMemory(appId);

        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            // HTML和多文件生成使用默认模型
            case HTML, MULTI_FILE ->
                    AiServices.builder(AiCodeGeneratorService.class)
                            .chatModel(chatModelProvider.getObject())
                            .streamingChatModel(openAiStreamingChatModel)
                            .chatMemory(chatMemory)
                            .build();

            default ->
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                            "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }



    /**
     * 默认bean
     *
     * @return
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return createAiCodeGeneratorService(0L, CodeGenTypeEnum.HTML);
    }

    private final Cache<String, Object> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                        log.info("AI服务实例被移除，appId:{},原因：{}", key, cause);
                    }
            )
            .build();

    /**
     * 根据appId和代码生成类型获取服务（带缓存）
     */
    public Object getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 获取 Vue 创建服务（只包含写入工具）
     */
    public AiVueCreateService getVueCreateService(long appId) {
        String cacheKey = "VUE_CREATE_" + appId;
        return (AiVueCreateService) serviceCache.get(cacheKey, key -> {
             MessageWindowChatMemory chatMemory = createChatMemory(appId);
            
            return AiServices.builder(AiVueCreateService.class)
                    .streamingChatModel(reasoningStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    // 创建模式只允许 writeFile
                    .tools(toolManager.getToolByName("FileWriteTool")) 
                    .hallucinatedToolNameStrategy(toolExecutionRequest ->
                            ToolExecutionResultMessage.from(toolExecutionRequest,
                                    "Error: there is no tool called " + toolExecutionRequest.name()))
                    .build();
        });
    }

    /**
     * 获取 Vue 修改服务（包含所有工具）
     */
    public AiVueModifyService getVueModifyService(long appId) {
        String cacheKey = "VUE_MODIFY_" + appId;
        return (AiVueModifyService) serviceCache.get(cacheKey, key -> {
             MessageWindowChatMemory chatMemory = createChatMemory(appId);

            return AiServices.builder(AiVueModifyService.class)
                    .streamingChatModel(reasoningStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    // 修改模式允许所有工具
                    .tools(toolManager.getAllTools())
                    .hallucinatedToolNameStrategy(toolExecutionRequest ->
                            ToolExecutionResultMessage.from(toolExecutionRequest,
                                    "Error: there is no tool called " + toolExecutionRequest.name()))
                    .build();
        });
    }

    /**
     * 删除应用时失效该应用的全部 AI 服务实例，避免已删除应用继续复用内存中的代理和记忆。
     */
    public void invalidateAppCache(long appId) {
        if (appId <= 0) {
            return;
        }
        for (CodeGenTypeEnum codeGenType : CodeGenTypeEnum.values()) {
            serviceCache.invalidate(buildCacheKey(appId, codeGenType));
        }
        serviceCache.invalidate("VUE_CREATE_" + appId);
        serviceCache.invalidate("VUE_MODIFY_" + appId);
    }

    private MessageWindowChatMemory createChatMemory(long appId) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(MAX_CHAT_MEMORY_MESSAGES)
                .build();
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, MAX_CHAT_MEMORY_MESSAGES);
        return chatMemory;
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }
}
