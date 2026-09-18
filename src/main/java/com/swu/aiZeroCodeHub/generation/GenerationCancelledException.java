package com.swu.aiZeroCodeHub.generation;

/**
 * 协作式取消信号。只在当前 run 内传播，不影响其他任务。
 */
public class GenerationCancelledException extends RuntimeException {
    public GenerationCancelledException() {
        super("生成任务已取消");
    }
}
