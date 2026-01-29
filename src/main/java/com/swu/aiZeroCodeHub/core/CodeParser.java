package com.swu.aiZeroCodeHub.core;

import com.swu.aiZeroCodeHub.model.vo.ai.HtmlCodeResult;
import com.swu.aiZeroCodeHub.model.vo.ai.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码解析器
 * 提供静态方法解析不同类型的代码内容
 */
public class CodeParser {
    private static final Pattern HTML_CODE_PATTERN=
            Pattern.compile("```html\\s*\\n([\\s\\S]*?)\\s*```",Pattern.CASE_INSENSITIVE);//不区分大小写
    private static final Pattern CSS_CODE_PATTERN=
            Pattern.compile("```css\\s*\\n([\\s\\S]*?)\\s*```",Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CODE_PATTERN=
            Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```",Pattern.CASE_INSENSITIVE);

    /**
     * 提取单文件代码
     * @param htmlCodeContent
     * @return
     */
    public static HtmlCodeResult parseHtmlCode(String htmlCodeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        //提取HTML代码
        String htmlCode=extractHtmlCode(htmlCodeContent);
        if (htmlCode!=null&&!htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        }
        else {
            result.setHtmlCode(htmlCode.trim());
        }
        return result;

    }

    /**
     * 提取多文件代码
     * @param multiFileCodeContent
     * @return
     */
    public static MultiFileCodeResult parseMultiFileCode(String multiFileCodeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();
        //提取各类代码
        String htmlCode = extractCodeByPattern(multiFileCodeContent, HTML_CODE_PATTERN);
        String cssCode = extractCodeByPattern(multiFileCodeContent, CSS_CODE_PATTERN);
        String jsCode = extractCodeByPattern(multiFileCodeContent, JS_CODE_PATTERN);

        if (htmlCode!=null&&!htmlCode.trim().isEmpty()){
            result.setHtmlCode(htmlCode.trim());
        }
        if (cssCode!=null&&!cssCode.trim().isEmpty()){
            result.setCssCode(cssCode.trim());
        }
        if (jsCode!=null&&!jsCode.trim().isEmpty()){
            result.setJsCode(jsCode.trim());
        }
        return result;
    }

    /**
     * 提取HTML代码内容
     * @param htmlCodeContent
     * @return
     */
    private static String extractHtmlCode(String htmlCodeContent) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(htmlCodeContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 根据正则模式提取代码
     * @param htmlCodeContent
     * @param pattern
     * @return
     */
    private static String extractCodeByPattern(String htmlCodeContent,Pattern pattern) {
        Matcher matcher = pattern.matcher(htmlCodeContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

}
