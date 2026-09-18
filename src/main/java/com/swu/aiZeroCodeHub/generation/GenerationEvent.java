package com.swu.aiZeroCodeHub.generation;

/**
 * 统一代码生成事件。只有 Controller 会把该对象转换为 SSE。
 *
 * @param type 事件类型
 * @param data 发送给前端的数据
 * @param sequence 当前运行内单调递增的事件序号；未经过分发器包装的内部事件为 0
 */
public record GenerationEvent(String type, Object data, long sequence) {

    public GenerationEvent(String type, Object data) {
        this(type, data, 0L);
    }

    public static GenerationEvent message(Object data) {
        return new GenerationEvent("message", data);
    }

    public static GenerationEvent workflowStart(Object data) {
        return new GenerationEvent("workflow_start", data);
    }

    public static GenerationEvent stepStarted(Object data) {
        return new GenerationEvent("step_started", data);
    }

    public static GenerationEvent stepCompleted(Object data) {
        return new GenerationEvent("step_completed", data);
    }

    public static GenerationEvent workflowCompleted(Object data) {
        return new GenerationEvent("workflow_completed", data);
    }

    public static GenerationEvent generationError(Object data) {
        return new GenerationEvent("generation_error", data);
    }

    /**
     * 保留 Java 调用方的工厂方法兼容性，但业务事件名已统一为 generation_error。
     */
    @Deprecated
    public static GenerationEvent error(Object data) {
        return generationError(data);
    }

    public static GenerationEvent runStarted(Object data) {
        return new GenerationEvent("run_started", data);
    }

    public static GenerationEvent cancelled(Object data) {
        return new GenerationEvent("cancelled", data);
    }

    public static GenerationEvent done(Object data) {
        return new GenerationEvent("done", data);
    }
}
