package com.swu.aiZeroCodeHub.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0,"OK"),
    PARAM_ERROR(40000,"请求参数错误"),
    NOT_LOGIN_ERROR(40100,"用户未登陆"),
    NO_AUTH_ERROR(400101,"用户无权限"),
    NOT_FOUND_ERROR(40400,"请求数据不存在"),
    FORBIDDEN_ERROR(40300,"禁止访问"),
    SYSTEM_ERROR(50000,"系统内部异常"),
    OPERATION_ERROR(50001,"操作失败")
    ;
    private int code;
    private String message;
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }


}
