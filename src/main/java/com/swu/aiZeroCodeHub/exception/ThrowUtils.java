package com.swu.aiZeroCodeHub.exception;

public class ThrowUtils {
    //根据条件抛异常
    public static void throwRuntimeExceptionByCondition(boolean condition,RuntimeException runtimeException) {
        if(condition) {
            throw runtimeException;
        }
    }
    //根据条件以及错误码抛对应异常
    public static void throwExceptionByConditionAndErrorCode(boolean condition,ErrorCode errorCode) {
        throwRuntimeExceptionByCondition(condition,new BusinessException(errorCode));
    }

    //根据条件以及错误码抛指定错误信息异常
    public static void throwExceptionByConditionAndErrorCodeAndMessage(boolean condition,ErrorCode errorCode,String message) {
        throwRuntimeExceptionByCondition(condition,new BusinessException(errorCode,message));
    }


}
