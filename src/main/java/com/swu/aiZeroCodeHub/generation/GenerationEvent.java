package com.swu.aiZeroCodeHub.generation;

/**
 * 统一代码生成事件。只有 Controller 会把该对象转换为 SSE。
 *
 * @param type 事件类型
 * @param data 发送给前端的数据
 */
public record GenerationEvent(String type, Object data) {

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

    public static GenerationEvent error(Object data) {
        return new GenerationEvent("error", data);
    }
}
