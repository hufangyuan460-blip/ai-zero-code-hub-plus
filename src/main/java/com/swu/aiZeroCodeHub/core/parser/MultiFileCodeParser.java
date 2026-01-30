package com.swu.aiZeroCodeHub.core.parser;

import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.MultiFileCodeResult;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多文件解析策略。
 *
 * <p>目标：从 AI 输出中分别提取：
 * <ul>
 *   <li>```html fenced block → index.html</li>
 *   <li>```css fenced block → style.css</li>
 *   <li>```js 或 ```javascript fenced block → script.js</li>
 * </ul>
 *
 * <p>容错策略：
 * <ul>
 *   <li>如果未提取到 html，则用原始文本作为 html 兜底（至少保证有可落盘的主文件）</li>
 *   <li>css/js 未匹配时返回空字符串（对应文件内容为空）</li>
 * </ul>
 */
@Component
public class MultiFileCodeParser implements CodeParserStrategy<MultiFileCodeResult> {

    private static final Pattern HTML_CODE_PATTERN =
            Pattern.compile("```html\\s*\\R([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CODE_PATTERN =
            Pattern.compile("```css\\s*\\R([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CODE_PATTERN =
            Pattern.compile("```(?:js|javascript)\\s*\\R([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    @Override
    public CodeGenTypeEnum getType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    public MultiFileCodeResult parse(String rawContent) {
        String safeRaw = rawContent == null ? "" : rawContent;
        String html = extractCodeByPattern(safeRaw, HTML_CODE_PATTERN);
        String css = extractCodeByPattern(safeRaw, CSS_CODE_PATTERN);
        String js = extractCodeByPattern(safeRaw, JS_CODE_PATTERN);

        if (html == null) {
            html = safeRaw;
        }

        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(html == null ? "" : html.trim());
        result.setCssCode(css == null ? "" : css.trim());
        result.setJsCode(js == null ? "" : js.trim());
        return result;
    }

    /**
     * 根据 fenced block 正则提取代码区内容。
     *
     * @param content 原始内容
     * @param pattern fenced block 的正则
     * @return 代码区内容；未匹配则返回 null
     */
    private String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
