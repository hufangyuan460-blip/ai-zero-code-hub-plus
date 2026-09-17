package com.swu.aiZeroCodeHub.langgraph4j;

import com.swu.aiZeroCodeHub.generation.GenerationEvent;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流节点事件的统一构造器。
 */
public final class WorkflowEventSupport {

    private WorkflowEventSupport() {
    }

    public static void stepStarted(WorkflowContext context, String step) {
        context.publishEvent(GenerationEvent.stepStarted(stepData(step, "进行中", context)));
    }

    public static void stepCompleted(WorkflowContext context, String step, String status) {
        context.publishEvent(GenerationEvent.stepCompleted(stepData(step, status, context)));
    }

    private static Map<String, Object> stepData(String step, String status, WorkflowContext context) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", step);
        data.put("status", status);
        data.put("repairAttempt", context.getRepairAttempt());
        return data;
    }
}
