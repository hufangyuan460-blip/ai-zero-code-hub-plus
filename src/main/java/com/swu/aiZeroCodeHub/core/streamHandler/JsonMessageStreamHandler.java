package com.swu.aiZeroCodeHub.core.streamHandler;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryAddRequest;
import com.swu.aiZeroCodeHub.model.enums.ChatHistoryMessageTypeEnum;
import com.swu.aiZeroCodeHub.model.enums.StreamMessageTypeEnum;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.message.AiResponseMessage;
import com.swu.aiZeroCodeHub.model.message.StreamMessage;
import com.swu.aiZeroCodeHub.model.message.ToolExecutedMessage;
import com.swu.aiZeroCodeHub.model.message.ToolRequestMessage;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import com.swu.aiZeroCodeHub.manager.ToolManager;
import com.swu.aiZeroCodeHub.aiTool.AiTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import dev.langchain4j.agent.tool.Tool;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JSON 消息流处理器
 * <p>
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {
    @Resource
    private SSEParser sseParser;
    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream （VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用 ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               Long appId,
                               User loginUser) {
        // 收集数据用于生成后端记忆格式
        HistoryAccumulator historyAccumulator = new HistoryAccumulator();
        Set<String> seenToolIds = new HashSet<>();

        return originFlux
                .flatMap(chunk -> {
                    // 使用SSE解析器处理
                    List<String> jsonMessages = sseParser.parseSSE(chunk);
                    return Flux.fromIterable(jsonMessages)
                            .map(json -> handleJsonMessageChunk(json, historyAccumulator, seenToolIds))
                            .filter(StrUtil::isNotEmpty);
                })
                .doOnComplete(() -> {
                    // 保存对话历史
                    String aiResponse = historyAccumulator.content();
                    if (StrUtil.isNotBlank(aiResponse)) {
                        saveChatHistory(appId, aiResponse, ChatHistoryMessageTypeEnum.AI, loginUser,chatHistoryService);
                    }
                })
                .doOnError(error -> {
                    log.error("处理SSE流失败", error);
                    saveChatHistory(appId, "AI回复失败: " + error.getMessage(),
                            ChatHistoryMessageTypeEnum.AI, loginUser,chatHistoryService);
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk,
                                          HistoryAccumulator historyAccumulator,
                                          Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());

        // 防止空指针（如果 typeEnum 为 null）
        if (typeEnum == null) {
            return "";
        }

        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                historyAccumulator.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);
                    
                    // 获取工具描述
                    String toolDescription = "调用工具";
                    if (toolName != null) {
                        AiTool tool = toolManager.getToolByName(toolName);
                        if (tool != null) {
                            // 尝试从注解获取描述
                            for (Method method : tool.getClass().getMethods()) {
                                if (method.isAnnotationPresent(Tool.class)) {
                                    Tool toolAnnotation = method.getAnnotation(Tool.class);
                                    String[] value = toolAnnotation.value();
                                    if (value.length > 0 && StrUtil.isNotBlank(value[0])) {
                                        toolDescription = value[0];
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    
                    return String.format("\n\n[选择工具] %s\n\n", toolDescription);
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = StrUtil.isBlank(toolExecutedMessage.getArguments())
                        ? JSONUtil.createObj()
                        : JSONUtil.parseObj(toolExecutedMessage.getArguments());
                
                // 尝试解析不同工具的参数
                String toolName = toolExecutedMessage.getName();
                String content = "";
                String path = "";
                String suffix = "";
                
                if (jsonObject.containsKey("relativeFilePath")) {
                    path = jsonObject.getStr("relativeFilePath");
                    suffix = FileUtil.getSuffix(path);
                } else if (jsonObject.containsKey("relativeDirPath")) {
                    path = jsonObject.getStr("relativeDirPath");
                }
                
                if (jsonObject.containsKey("content")) {
                    content = jsonObject.getStr("content");
                } else if (jsonObject.containsKey("oldContent") && jsonObject.containsKey("newContent")) {
                    // 修改文件工具
                    content = "Old:\n" + jsonObject.getStr("oldContent") + "\n\nNew:\n" + jsonObject.getStr("newContent");
                }
                
                String result = "";
                if ("writeFile".equals(toolName) || "modifyFile".equals(toolName)) {
                     result = String.format("""
                        [工具调用] %s %s
                        ```%s
                        %s
                        ```
                        """, toolName, path, suffix, content);
                } else if ("readFile".equals(toolName)) {
                     result = String.format("[工具调用] 读取文件 %s", path);
                } else if ("deleteFile".equals(toolName)) {
                     result = String.format("[工具调用] 删除文件 %s", path);
                } else if ("getProjectFileTree".equals(toolName)) {
                     result = String.format("[工具调用] 获取目录结构 %s", path);
                } else {
                     // 默认展示给前端，历史记录不复用完整参数
                     result = String.format("[工具调用] %s %s", toolName, jsonObject.toString());
                }

                historyAccumulator.append("\n\n" + buildToolHistorySummary(toolName, path) + "\n\n");

                // 前端可以继续展示代码内容，但持久化历史只保留工具摘要
                String output = String.format("\n\n%s\n\n", result);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }

    private String buildToolHistorySummary(String toolName, String path) {
        String target = StrUtil.isBlank(path) ? "" : "：" + path;
        if ("writeFile".equals(toolName)) {
            return "已写入文件" + target;
        }
        if ("modifyFile".equals(toolName)) {
            return "已修改文件" + target;
        }
        if ("readFile".equals(toolName)) {
            return "已读取文件" + target;
        }
        if ("deleteFile".equals(toolName)) {
            return "已删除文件" + target;
        }
        if ("getProjectFileTree".equals(toolName)) {
            return "已获取目录结构" + target;
        }
        return "工具执行完成：" + StrUtil.blankToDefault(toolName, "未知工具") + target;
    }

    private static class HistoryAccumulator {
        private final StringBuilder content = new StringBuilder();
        private boolean tooLong;

        private void append(String value) {
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

        private String content() {
            return tooLong
                    ? ChatHistoryService.OVERSIZED_AI_HISTORY_PLACEHOLDER
                    : content.toString();
        }
    }


    /**
     * 保存对话历史
     *
     * @param appId
     * @param content
     * @param messageTypeEnum
     * @param loginUser
     */
    private void saveChatHistory(Long appId, String content, ChatHistoryMessageTypeEnum messageTypeEnum, User loginUser,ChatHistoryService chatHistoryService) {
        try {
            ChatHistoryAddRequest addRequest = new ChatHistoryAddRequest();
            addRequest.setAppId(appId);
            addRequest.setContent(content);
            addRequest.setMessageType(messageTypeEnum.getValue());
            chatHistoryService.addChatHistory(addRequest, loginUser);
        } catch (Exception e) {
            log.error("保存对话历史失败: appId={}, type={}, error={}", appId, messageTypeEnum.getText(), e.getMessage());
        }
    }
}
