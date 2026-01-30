package com.swu.aiZeroCodeHub.model.vo.ai;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 单文件 HTML 模式的生成结果。
 *
 * <p>约定：
 * <ul>
 *   <li>{@code htmlCode}：通常为完整的 HTML 文档字符串</li>
 *   <li>该 HTML 内通常包含内联的 {@code <style>} 与 {@code <script>}，以便“一文件可运行”</li>
 * </ul>
 */
@Data
public class HtmlCodeResult implements CodeResult {
    @Description("HTML代码")
    private String htmlCode;
    @Description("生成代码的描述")
    private String description;
}
