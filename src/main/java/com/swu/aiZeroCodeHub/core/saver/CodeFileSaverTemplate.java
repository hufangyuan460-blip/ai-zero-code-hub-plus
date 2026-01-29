package com.swu.aiZeroCodeHub.core.saver;

import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.CodeResult;

import java.io.File;

public interface CodeFileSaverTemplate {

    CodeGenTypeEnum getType();

    File save(CodeResult codeResult);
}
