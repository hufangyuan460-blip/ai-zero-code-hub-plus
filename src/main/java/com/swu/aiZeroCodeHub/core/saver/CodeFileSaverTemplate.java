package com.swu.aiZeroCodeHub.core.saver;

import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.CodeResult;

import java.io.File;

/**
 * 保存模板：将 {@link CodeResult} 按类型落盘并返回目录。
 */
public interface CodeFileSaverTemplate {

    CodeGenTypeEnum getType();

    File save(CodeResult codeResult, Long appId);
}
