package com.swu.aiZeroCodeHub.core.executor;

import com.swu.aiZeroCodeHub.core.saver.CodeFileSaverTemplate;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.CodeResult;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 保存执行器：按 {@link CodeGenTypeEnum} 选择 {@link CodeFileSaverTemplate} 并落盘。
 */
@Service
public class CodeFileSaverExecutor {

    private final Map<CodeGenTypeEnum, CodeFileSaverTemplate> templateMap;

    public CodeFileSaverExecutor(List<CodeFileSaverTemplate> templates) {
        Map<CodeGenTypeEnum, CodeFileSaverTemplate> map = new EnumMap<>(CodeGenTypeEnum.class);
        for (CodeFileSaverTemplate template : templates) {
            map.put(template.getType(), template);
        }
        this.templateMap = Map.copyOf(map);
    }

    public File save(CodeResult codeResult, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生成类型为空");
        }
        CodeFileSaverTemplate template = templateMap.get(codeGenTypeEnum);
        if (template == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的保存类型：" + codeGenTypeEnum.getValue());
        }
        return template.save(codeResult);
    }
}
