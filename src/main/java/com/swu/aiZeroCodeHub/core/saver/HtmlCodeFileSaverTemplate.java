package com.swu.aiZeroCodeHub.core.saver;

import com.swu.aiZeroCodeHub.config.CodeOutputProperties;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.HtmlCodeResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HtmlCodeFileSaverTemplate extends AbstractCodeFileSaverTemplate<HtmlCodeResult> {

    public HtmlCodeFileSaverTemplate(CodeOutputProperties codeOutputProperties) {
        super(codeOutputProperties);
    }

    @Override
    public CodeGenTypeEnum getType() {
        return CodeGenTypeEnum.HTML;
    }

    @Override
    protected Class<HtmlCodeResult> getCodeResultClass() {
        return HtmlCodeResult.class;
    }

    @Override
    protected Map<String, String> buildFiles(HtmlCodeResult codeResult) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("index.html", codeResult.getHtmlCode());
        return files;
    }
}
