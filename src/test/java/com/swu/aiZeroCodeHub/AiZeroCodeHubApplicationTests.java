package com.swu.aiZeroCodeHub;

import com.swu.aiZeroCodeHub.aiService.AiCodeGeneratorService;
import com.swu.aiZeroCodeHub.core.AiCodeGeneratorFacade;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.HtmlCodeResult;
import com.swu.aiZeroCodeHub.model.vo.ai.MultiFileCodeResult;
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




}
