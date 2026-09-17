package com.swu.aiZeroCodeHub.model.enums;

import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionModeEnumTest {

    @Test
    void missingModeKeepsDirectCompatibility() {
        assertEquals(ExecutionModeEnum.DIRECT, ExecutionModeEnum.parse(null));
        assertEquals(ExecutionModeEnum.DIRECT, ExecutionModeEnum.parse("  "));
        assertEquals(ExecutionModeEnum.WORKFLOW, ExecutionModeEnum.parse("workflow"));
    }

    @Test
    void unknownModeIsParameterError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> ExecutionModeEnum.parse("UNKNOWN"));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }
}
