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

import java.io.File;

@SpringBootTest
class AiZeroCodeHubApplicationTests {
    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateHtmlCode() {
        HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode("做一个工作记录小工具");
        Assertions.assertNotNull(htmlCodeResult);
    }


    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode("做一个留言板");
        Assertions.assertNotNull(multiFileCodeResult);
    }


    @Test
    void generateAndSaveCode(){
        File file = aiCodeGeneratorFacade.generateAndSaveCode("生成一个留言板", CodeGenTypeEnum.HTML);
        Assertions.assertNotNull(file);
    }




}
