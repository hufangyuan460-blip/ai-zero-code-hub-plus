package com.swu.aiZeroCodeHub.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.swu.aiZeroCodeHub.aiService.AiCodeGeneratorService;
import com.swu.aiZeroCodeHub.aiTool.FileWriteTool;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;



@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {
    @Resource
    private ChatModel chatModel;
    @Resource
    private StreamingChatModel openAiStreamingChatModel;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private StreamingChatModel reasoningStreamingChatModel;
    @Resource
    @org.springframework.context.annotation.Lazy
    private FileWriteTool fileWriteTool;

    // 解决循环依赖：AiCodeGeneratorFacade 依赖 Factory，Factory 依赖 Facade (间接) 导致构造失败
    // 但实际上 Factory 并不依赖 Facade，问题出在 Facade 注入 Factory 时可能未初始化完成
    // 或者是 Facade 的构造函数注入有问题
    // 在 AiCodeGeneratorFacade 中，CodeParserExecutor 和 CodeFileSaverExecutor 是通过字段注入的
    // 而这两个类都使用了 @Service 且有构造函数注入
    // 检查 AiCodeGeneratorFacade 自身是否有构造函数抛出异常
    // 日志显示: Failed to instantiate [com.swu.aiZeroCodeHub.core.AiCodeGeneratorFacade]: Constructor threw exception
    // 看起来是 Facade 的构造函数或字段注入时出了问题

    /**
     * 根据生成类型创建AiCodeGeneratorService实例
     * @param appId
     * @param codeGenType
     * @return
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        // 根据appId构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(Integer.MAX_VALUE)
                .build();
        //从数据库加载历史数据到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, Integer.MAX_VALUE);



        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            // Vue项目生成使用推理模型
            case VUE_PROJECT ->
                    AiServices.builder(AiCodeGeneratorService.class)
                            .streamingChatModel(reasoningStreamingChatModel)
                            .chatMemoryProvider(memoryId -> chatMemory)
                            .tools(fileWriteTool)
                            //幻觉工具名称策略
                            .hallucinatedToolNameStrategy(toolExecutionRequest ->
                                    ToolExecutionResultMessage.from(toolExecutionRequest,
                                            "Error: there is no tool called " + toolExecutionRequest.name()))
                            .build();

            // HTML和多文件生成使用默认模型
            case HTML, MULTI_FILE ->
                    AiServices.builder(AiCodeGeneratorService.class)
                            .chatModel(chatModel)
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

    /**
     * ai服务实例缓存
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
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
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }
}
