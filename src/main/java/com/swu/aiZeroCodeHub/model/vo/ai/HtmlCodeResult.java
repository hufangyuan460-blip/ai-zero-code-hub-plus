package com.swu.aiZeroCodeHub.model.vo.ai;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class HtmlCodeResult implements CodeResult {
    @Description("HTML代码")
    private String htmlCode;
    @Description("生成代码的描述")
    private String description;
}
