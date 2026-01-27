package com.swu.aiZeroCodeHub.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    /**
     * 直接使用错误码和消息创建业务异常
     * @param code 自定义错误码，用于精确标识异常类型
     * @param message 详细的错误描述信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 通过预定义的错误码枚举创建业务异常
     * @param errorCode 错误码枚举实例，包含标准化的错误码和消息
     */
    public BusinessException(ErrorCode errorCode){
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 基于错误码枚举但使用自定义消息创建业务异常
     * @param errorCode 错误码枚举实例，提供标准错误码
     * @param message 自定义的错误消息，覆盖枚举中的默认消息
     */
    public BusinessException(ErrorCode errorCode, String message){
        super(message);
        this.code = errorCode.getCode();
    }
}