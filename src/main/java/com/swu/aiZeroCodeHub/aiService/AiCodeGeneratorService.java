package com.swu.aiZeroCodeHub.aiService;

import com.swu.aiZeroCodeHub.model.vo.ai.HtmlCodeResult;
import com.swu.aiZeroCodeHub.model.vo.ai.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface AiCodeGeneratorService {


    /**
     * 生成单文件网页代码（流式）
     * @param userMessage
     * @return
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    /**
     * 生成多文件网页代码（流式）
     * @param userMessage
     * @return
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

    /**
     * 生成vue项目代码(流式)
     * 必须加上 @MemoryId Long appId，类型要和 Tool 中的 @ToolMemoryId 一致
     */
    @SystemMessage(fromResource = "prompt/codegen-vueProject-system-prompt.txt")
    TokenStream generateProjectCodeStream(
            @MemoryId Long appId,
            @UserMessage String userMessage
    );

}
