package com.swu.aiZeroCodeHub.core.parser;

import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.CodeResult;

/**
 * 解析策略：将 AI 原始输出解析为结构化 {@link CodeResult}。
 */
public interface CodeParserStrategy<T extends CodeResult> {

    CodeGenTypeEnum getType();

    T parse(String rawContent);
}
