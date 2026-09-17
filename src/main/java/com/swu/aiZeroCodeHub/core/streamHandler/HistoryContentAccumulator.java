package com.swu.aiZeroCodeHub.core.streamHandler;

import com.swu.aiZeroCodeHub.service.ChatHistoryService;

/**
 * 受大小限制的 AI 历史内容收集器。超限后只保留统一占位文本。
 */
public final class HistoryContentAccumulator {

    private final StringBuilder content = new StringBuilder();
    private boolean tooLong;

    public void append(String value) {
        if (tooLong || value == null) {
            return;
        }
        if (content.length() + value.length() > ChatHistoryService.MAX_AI_HISTORY_LENGTH) {
            content.setLength(0);
            tooLong = true;
            return;
        }
        content.append(value);
    }

    public String content() {
        return tooLong
                ? ChatHistoryService.OVERSIZED_AI_HISTORY_PLACEHOLDER
                : content.toString();
    }
}
