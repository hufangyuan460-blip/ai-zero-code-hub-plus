package com.swu.aiZeroCodeHub.guardrail;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;

import java.util.List;

public class SensitiveInfoOutputGuardrail implements OutputGuardrail {

    private static final List<String> KEYWORDS = List.of(
            "BEGIN PRIVATE KEY",
            "AKID",
            "sk-",
            "DEEPSEEK_API_KEY",
            "TENCENT_CLOUD_SECRET_KEY",
            "DATABASE_PASSWORD",
            "Authorization:",
            "Bearer ",
            "secretId",
            "secretKey"
    );

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        if (request == null || request.responseFromLLM() == null || request.responseFromLLM().aiMessage() == null) {
            return success();
        }
        String text = request.responseFromLLM().aiMessage().text();
        if (StrUtil.isBlank(text)) {
            return success();
        }
        for (String keyword : KEYWORDS) {
            if (StrUtil.isNotBlank(keyword) && text.contains(keyword)) {
                return fatal("响应内容包含敏感信息，已拦截输出");
            }
        }
        return success();
    }
}
