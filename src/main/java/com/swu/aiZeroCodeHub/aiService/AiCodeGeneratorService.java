package com.swu.aiZeroCodeHub.aiService;

import com.swu.aiZeroCodeHub.model.vo.ai.HtmlCodeResult;
import com.swu.aiZeroCodeHub.model.vo.ai.MultiFileCodeResult;
import com.swu.aiZeroCodeHub.guardrail.PromptInjectionInputGuardrail;
import com.swu.aiZeroCodeHub.guardrail.SensitiveInfoOutputGuardrail;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import reactor.core.publisher.Flux;

public interface AiCodeGeneratorService {


    /**
     * 生成单文件网页代码（流式）
     * @param userMessage
     * @return
     */
    @InputGuardrails(PromptInjectionInputGuardrail.class)
    @OutputGuardrails(SensitiveInfoOutputGuardrail.class)
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(@UserMessage String userMessage);

    /**
     * 生成多文件网页代码（流式）
     * @param userMessage
     * @return
     */
    @InputGuardrails(PromptInjectionInputGuardrail.class)
    @OutputGuardrails(SensitiveInfoOutputGuardrail.class)
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(@UserMessage String userMessage);

}
