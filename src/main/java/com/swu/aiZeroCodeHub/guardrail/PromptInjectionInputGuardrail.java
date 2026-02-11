package com.swu.aiZeroCodeHub.guardrail;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.ArrayList;
import java.util.List;

public class PromptInjectionInputGuardrail implements InputGuardrail {

    private static final List<String> KEYWORDS = List.of(
            "ignore previous",
            "disregard previous",
            "system prompt",
            "developer message",
            "reveal",
            "leak",
            "api key",
            "access token",
            "secret key",
            "ssh",
            "/etc/passwd",
            "powershell",
            "cmd.exe",
            "rm -rf",
            "curl ",
            "wget ",
            "base64",
            "忽略之前",
            "忽略上面",
            "系统提示",
            "开发者消息",
            "泄露",
            "密钥",
            "令牌",
            "越权",
            "绕过",
            "读取服务器",
            "读取本地文件"
    );

    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        if (request == null || request.userMessage() == null) {
            return success();
        }
        String text = extractUserText(request.userMessage());
        if (StrUtil.isBlank(text)) {
            return success();
        }
        String lower = text.toLowerCase();
        for (String keyword : KEYWORDS) {
            if (StrUtil.isBlank(keyword)) {
                continue;
            }
            if (lower.contains(keyword) || text.contains(keyword)) {
                return fatal("检测到可能的恶意提示词或越权请求，已拒绝执行");
            }
        }
        return success();
    }

    private String extractUserText(UserMessage userMessage) {
        if (userMessage == null) {
            return null;
        }
        if (userMessage.hasSingleText()) {
            return userMessage.singleText();
        }
        List<Content> contents = userMessage.contents();
        if (contents == null || contents.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Content content : contents) {
            if (content instanceof TextContent) {
                String part = ((TextContent) content).text();
                if (StrUtil.isNotBlank(part)) {
                    parts.add(part);
                }
            }
        }
        return String.join("\n", parts);
    }
}
