package com.swu.aiZeroCodeHub.core.parser;

import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.MultiFileCodeResult;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
