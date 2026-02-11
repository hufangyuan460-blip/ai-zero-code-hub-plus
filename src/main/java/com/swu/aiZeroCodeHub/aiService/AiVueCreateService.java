package com.swu.aiZeroCodeHub.aiService;

import com.swu.aiZeroCodeHub.guardrail.PromptInjectionInputGuardrail;
import com.swu.aiZeroCodeHub.guardrail.SensitiveInfoOutputGuardrail;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * Vue 项目创建服务
 */
public interface AiVueCreateService {

    @SystemMessage(fromResource = "prompt/codegen-vue-create-system-prompt.txt")
    @InputGuardrails(PromptInjectionInputGuardrail.class)
    @OutputGuardrails(SensitiveInfoOutputGuardrail.class)
    TokenStream generateProjectCodeStream(
            @MemoryId Long appId,
            @UserMessage String userMessage
    );
}
