package com.swu.aiZeroCodeHub.core.streamHandler;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SSEParser {
    /**
     * 解析SSE数据行
     */
    public String parseSSELine(String line) {
        if (StrUtil.isBlank(line)) {
            return null;
        }

        // 跳过注释
        if (line.startsWith(":")) {
            return null;
        }

        // 解析 data: 前缀
        if (line.startsWith("data: ")) {
            String data = line.substring(6).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) {
                return null;
            }
            return data;
        }

        // 如果是JSON数据但没加data:前缀，直接返回
        if (line.trim().startsWith("{") || line.trim().startsWith("[")) {
            return line.trim();
        }

        return null;
    }

    /**
     * 批量解析SSE数据
     */
    public List<String> parseSSE(String sseData) {
        if (StrUtil.isBlank(sseData)) {
            return List.of();
        }

        return Arrays.stream(sseData.split("\n"))
                .map(this::parseSSELine)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
