package com.swu.aiZeroCodeHub.core.parser;

import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.HtmlCodeResult;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
