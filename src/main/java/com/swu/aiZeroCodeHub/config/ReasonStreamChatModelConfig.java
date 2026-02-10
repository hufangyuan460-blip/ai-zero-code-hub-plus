package com.swu.aiZeroCodeHub.config;


import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
@Data
public class ReasonStreamChatModelConfig {
    private String baseUrl;
    private String apiKey;

    /**
     * 流式推理模型（用于工程项目生成，带工具调用）
     * @return
     */
    @Bean
    public StreamingChatModel reasoningStreamingChatModel(){
        final String modelName = "deepseek-chat";
        final int maxToken=8192;
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxToken)
                .logResponses(true)
                .logRequests(true)
                .timeout(Duration.ofSeconds(600))
                .build();


    }
}
