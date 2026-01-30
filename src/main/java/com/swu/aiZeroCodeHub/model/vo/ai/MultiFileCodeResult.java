package com.swu.aiZeroCodeHub.model.vo.ai;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 多文件模式的生成结果。
 *
 * <p>约定：
 * <ul>
 *   <li>{@code htmlCode}：index.html 内容</li>
 *   <li>{@code cssCode}：style.css 内容</li>
 *   <li>{@code jsCode}：script.js 内容</li>
 * </ul>
 *
 * <p>保存时会按照上述文件名落盘，便于直接用浏览器打开 index.html 运行。
 */
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
