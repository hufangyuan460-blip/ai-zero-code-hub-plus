package com.swu.aiZeroCodeHub.generation;

/**
 * 生成节点达到服务端时限后的内部控制信号。
 */
public class GenerationTimeoutException extends RuntimeException {

    public GenerationTimeoutException(String message) {
        super(message);
    }

    public GenerationTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
