package com.swu.aiZeroCodeHub.model.vo.ai;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class MultiFileCodeResult implements CodeResult {
    @Description("HTML代码")
    private String htmlCode;
    @Description("CSS代码")
    private String cssCode;
    @Description("JS代码")
    private String jsCode;
    @Description("生成代码的描述")
    private String description;

}
