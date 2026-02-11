package com.swu.aiZeroCodeHub.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片收集规划服务工厂
 */
@Configuration
public class ImageCollectionPlanServiceFactory {

    @Autowired
    @Qualifier("openAiChatModelPrototype")
    private ObjectProvider<ChatModel> chatModelProvider;

    @Bean
    public ImageCollectionPlanService createImageCollectionPlanService() {
        return AiServices.builder(ImageCollectionPlanService.class)
                .chatModel(chatModelProvider.getObject())
                .build();
    }
}
