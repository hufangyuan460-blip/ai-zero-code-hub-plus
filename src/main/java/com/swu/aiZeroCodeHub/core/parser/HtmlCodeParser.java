package com.swu.aiZeroCodeHub.core.parser;

import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.HtmlCodeResult;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 单文件解析策略。
 *
 * <p>目标：从 AI 输出中提取 ```html fenced block 中的内容，组装为 {@link HtmlCodeResult}。
 *
 * <p>容错策略：
 * <ul>
 *   <li>如果找不到 fenced block，则将原始文本作为 htmlCode 兜底（避免保存空文件）</li>
 *   <li>输入 rawContent 为 null 时按空字符串处理</li>
 * </ul>
 */
@Component
public class HtmlCodeParser implements CodeParserStrategy<HtmlCodeResult> {

    private static final Pattern HTML_CODE_PATTERN =
            Pattern.compile("```html\\s*\\R([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    @Override
    public CodeGenTypeEnum getType() {
        return CodeGenTypeEnum.HTML;
    }

    @Override
    public HtmlCodeResult parse(String rawContent) {
        String safeRaw = rawContent == null ? "" : rawContent;
        String html = extractCodeByPattern(safeRaw, HTML_CODE_PATTERN);
        if (html == null) {
            html = safeRaw;
        }
        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode(html == null ? "" : html.trim());
        return result;
    }

    /**
     * 使用指定正则从原文中提取 fenced block 的代码区内容。
     *
     * @param content 原始内容
     * @param pattern fenced block 的正则
     * @return 提取到的代码区内容；未匹配则返回 null
     */
    private String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
