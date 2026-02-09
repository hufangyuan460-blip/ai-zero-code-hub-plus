package com.swu.aiZeroCodeHub.aiService;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * Vue 项目修改服务
 */
public interface AiVueModifyService {

    @SystemMessage(fromResource = "prompt/codegen-vue-modify-system-prompt.txt")
    TokenStream generateProjectCodeStream(
            @MemoryId Long appId,
            @UserMessage String userMessage
    );
}
