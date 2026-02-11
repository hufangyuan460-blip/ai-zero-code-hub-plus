package com.swu.aiZeroCodeHub.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

import java.time.Duration;

@Configuration
public class OpenAiChatModelPrototypeConfig {

    @Bean(name = "openAiChatModelPrototype")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ChatModel openAiChatModel(Environment environment) {
        String baseUrl = environment.getProperty("langchain4j.open-ai.chat-model.base-url");
        String apiKey = environment.getProperty("langchain4j.open-ai.chat-model.api-key");
        String modelName = environment.getProperty("langchain4j.open-ai.chat-model.model-name");
        Integer maxTokens = environment.getProperty("langchain4j.open-ai.chat-model.max-tokens", Integer.class);
        Integer timeoutSeconds = environment.getProperty("langchain4j.open-ai.chat-model.timeout-seconds", Integer.class);
        Boolean strictJsonSchema = environment.getProperty("langchain4j.open-ai.chat-model.strict-json-schema", Boolean.class);
        String responseFormat = environment.getProperty("langchain4j.open-ai.chat-model.response-format");
        Boolean logRequests = environment.getProperty("langchain4j.open-ai.chat-model.log-requests", Boolean.class);
        Boolean logResponses = environment.getProperty("langchain4j.open-ai.chat-model.log-responses", Boolean.class);

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName);

        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (timeoutSeconds != null) {
            builder.timeout(Duration.ofSeconds(timeoutSeconds));
        }
        if (strictJsonSchema != null) {
            builder.strictJsonSchema(strictJsonSchema);
        }
        if (responseFormat != null) {
            builder.responseFormat(responseFormat);
        }
        if (logRequests != null) {
            builder.logRequests(logRequests);
        }
        if (logResponses != null) {
            builder.logResponses(logResponses);
        }
        return builder.build();
    }
}
