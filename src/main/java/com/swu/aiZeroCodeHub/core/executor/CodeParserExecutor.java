package com.swu.aiZeroCodeHub.core.executor;

import com.swu.aiZeroCodeHub.core.parser.CodeParserStrategy;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.CodeResult;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 解析执行器：按 {@link CodeGenTypeEnum} 选择 {@link CodeParserStrategy} 并执行解析。
 */
@Service
public class CodeParserExecutor {

    private final Map<CodeGenTypeEnum, CodeParserStrategy<? extends CodeResult>> strategyMap;

    public CodeParserExecutor(List<CodeParserStrategy<? extends CodeResult>> strategies) {
        Map<CodeGenTypeEnum, CodeParserStrategy<? extends CodeResult>> map = new EnumMap<>(CodeGenTypeEnum.class);
        for (CodeParserStrategy<? extends CodeResult> strategy : strategies) {
            map.put(strategy.getType(), strategy);
        }
        this.strategyMap = Map.copyOf(map);
    }

    public CodeResult parse(String rawContent, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生成类型为空");
        }
        CodeParserStrategy<? extends CodeResult> strategy = strategyMap.get(codeGenTypeEnum);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的解析类型：" + codeGenTypeEnum.getValue());
        }
        return strategy.parse(rawContent);
    }
}
