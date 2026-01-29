package com.swu.aiZeroCodeHub.core.parser;

import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.CodeResult;

public interface CodeParserStrategy<T extends CodeResult> {

    CodeGenTypeEnum getType();

    T parse(String rawContent);
}
