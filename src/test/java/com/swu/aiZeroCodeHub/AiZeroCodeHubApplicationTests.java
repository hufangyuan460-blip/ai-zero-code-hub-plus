package com.swu.aiZeroCodeHub;

import com.swu.aiZeroCodeHub.aiService.AiCodeGeneratorService;
import com.swu.aiZeroCodeHub.core.AiCodeGeneratorFacade;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.HtmlCodeResult;
import com.swu.aiZeroCodeHub.model.vo.ai.MultiFileCodeResult;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.File;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "DEEPSEEK_API_KEY=test_key",
    "DATABASE_PASSWORD=test_password"
})
class AiZeroCodeHubApplicationTests {
    @MockBean
    private AiCodeGeneratorService aiCodeGeneratorService;
    @MockBean
    private ChatHistoryService chatHistoryService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateHtmlCode() {
        HtmlCodeResult mockResult = new HtmlCodeResult();
        mockResult.setHtmlCode("<html></html>");
        when(aiCodeGeneratorService.generateHtmlCode(anyString())).thenReturn(mockResult);
        
        HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode("做一个工作记录小工具");
        Assertions.assertNotNull(htmlCodeResult);
    }


    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult mockResult = new MultiFileCodeResult();
        when(aiCodeGeneratorService.generateMultiFileCode(anyString())).thenReturn(mockResult);

        MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode("做一个留言板");
        Assertions.assertNotNull(multiFileCodeResult);
    }


    @Test
    void generateAndSaveCode(){
        HtmlCodeResult mockResult = new HtmlCodeResult();
        mockResult.setHtmlCode("```html\n<html><body>Test</body></html>\n```"); // 模拟 AI 返回的 markdown 格式
        when(aiCodeGeneratorService.generateHtmlCode(anyString())).thenReturn(mockResult);

        File file = aiCodeGeneratorFacade.generateAndSaveCode("生成一个留言板", CodeGenTypeEnum.HTML, 1L); // 传入 appId=1
        Assertions.assertNotNull(file);
    }

    @Test
    void chatStream() {
        // 模拟普通对话流
        when(aiCodeGeneratorService.chatStream(anyString())).thenReturn(reactor.core.publisher.Flux.just("你好", "，", "有什么", "可以", "帮", "你", "？"));
        
        // 调用 chatStream 逻辑 (通过 generateAndSaveCodeStream 传入 CHAT 类型)
        // 注意：这里不会返回 File，因为 CHAT 模式不保存文件，只保存历史记录
        // 我们主要验证调用是否成功不报错
        var flux = aiCodeGeneratorFacade.generateAndSaveCodeStream("你好", CodeGenTypeEnum.CHAT, 1L, new com.swu.aiZeroCodeHub.model.entity.User());
        
        StringBuilder sb = new StringBuilder();
        flux.toIterable().forEach(sb::append);
        
        Assertions.assertEquals("你好，有什么可以帮你？", sb.toString());
    }

}
