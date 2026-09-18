package com.swu.aiZeroCodeHub.generation;

/**
 * Agent 生成运行状态。
 */
public enum GenerationRunStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == SUCCEEDED
                || this == FAILED
                || this == CANCELLED
                || this == TIMED_OUT;
    }
}
