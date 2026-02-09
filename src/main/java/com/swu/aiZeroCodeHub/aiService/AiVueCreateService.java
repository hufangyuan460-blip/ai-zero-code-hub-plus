package com.swu.aiZeroCodeHub.aiService;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * Vue 项目创建服务
 */
public interface AiVueCreateService {

    @SystemMessage(fromResource = "prompt/codegen-vue-create-system-prompt.txt")
    TokenStream generateProjectCodeStream(
            @MemoryId Long appId,
            @UserMessage String userMessage
    );
}
