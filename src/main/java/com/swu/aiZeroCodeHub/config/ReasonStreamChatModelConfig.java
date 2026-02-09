package com.swu.aiZeroCodeHub.config;


import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
@Data
public class ReasonStreamChatModelConfig {
    private String baseUrl;
    private String apiKey;
    private Integer maxTokens;
    private Integer timeoutSeconds;

    /**
     * 流式推理模型（用于工程项目生成，带工具调用）
     * @return
     */
    @Bean
    public StreamingChatModel reasoningStreamingChatModel(){
        final String modelName = "deepseek-chat";
        final int maxToken = (maxTokens != null && maxTokens > 0) ? Math.min(maxTokens, 8192) : 8192;
        final int timeout = (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 180;
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxToken)
                .maxCompletionTokens(maxToken)
                .strictTools(true)
                .logResponses(true)
                .logRequests(true)
                .timeout(java.time.Duration.ofSeconds(timeout))
                .build();


    }
}
