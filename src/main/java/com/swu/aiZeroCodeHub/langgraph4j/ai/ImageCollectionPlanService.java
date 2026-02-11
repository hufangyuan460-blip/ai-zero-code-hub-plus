package com.swu.aiZeroCodeHub.langgraph4j.ai;


import com.swu.aiZeroCodeHub.langgraph4j.model.ImageCollectionPlan;
import com.swu.aiZeroCodeHub.guardrail.PromptInjectionInputGuardrail;
import com.swu.aiZeroCodeHub.guardrail.SensitiveInfoOutputGuardrail;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * 图片收集规划服务
 */
public interface ImageCollectionPlanService {

    /**
     * 根据用户提示词分析需要收集的图片类型和参数
     */
    @SystemMessage(fromResource = "prompt/image-collection-plan-system-prompt.txt")
    @InputGuardrails(PromptInjectionInputGuardrail.class)
    @OutputGuardrails(SensitiveInfoOutputGuardrail.class)
    ImageCollectionPlan planImageCollection(@UserMessage String userPrompt);
}
