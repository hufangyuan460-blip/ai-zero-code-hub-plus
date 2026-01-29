package com.swu.aiZeroCodeHub.core.saver;

import com.swu.aiZeroCodeHub.config.CodeOutputProperties;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.MultiFileCodeResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MultiFileCodeFileSaverTemplate extends AbstractCodeFileSaverTemplate<MultiFileCodeResult> {

    public MultiFileCodeFileSaverTemplate(CodeOutputProperties codeOutputProperties) {
        super(codeOutputProperties);
    }

    @Override
    public CodeGenTypeEnum getType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected Class<MultiFileCodeResult> getCodeResultClass() {
        return MultiFileCodeResult.class;
    }

    @Override
    protected Map<String, String> buildFiles(MultiFileCodeResult codeResult) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("index.html", codeResult.getHtmlCode());
        files.put("style.css", codeResult.getCssCode());
        files.put("script.js", codeResult.getJsCode());
        return files;
    }
}
