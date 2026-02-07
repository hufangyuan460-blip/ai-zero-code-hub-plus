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
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        Set<String> seenToolIds = new HashSet<>();

        return originFlux
                .flatMap(chunk -> {
                    // 使用SSE解析器处理
                    List<String> jsonMessages = sseParser.parseSSE(chunk);
                    return Flux.fromIterable(jsonMessages)
                            .map(json -> handleJsonMessageChunk(json, chatHistoryStringBuilder, seenToolIds))
                            .filter(StrUtil::isNotEmpty);
                })
                .doOnComplete(() -> {
                    // 保存对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    log.info("SSE流处理完成，保存对话历史长度: {}", aiResponse.length());
                    if (StrUtil.isNotBlank(aiResponse)) {
                        saveChatHistory(appId, aiResponse, ChatHistoryMessageTypeEnum.AI, loginUser,chatHistoryService);
                    }
                })
                .doOnCancel(() -> {
                    // 客户端断开连接时，也保存已生成的内容
                    log.info("客户端断开连接，保存已生成的对话历史");
                    String aiResponse = chatHistoryStringBuilder.toString();
                    if (StrUtil.isNotBlank(aiResponse)) {
                        // 标记为中断
                        aiResponse += "\n\n[连接中断，生成已停止]";
                        saveChatHistory(appId, aiResponse, ChatHistoryMessageTypeEnum.AI, loginUser, chatHistoryService);
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
                                          StringBuilder chatHistoryStringBuilder,
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
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);
                    return "\n\n[选择工具] 写入文件\n\n";
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                String relativeFilePath = jsonObject.getStr("relativeFilePath");
                String suffix = FileUtil.getSuffix(relativeFilePath);
                String content = jsonObject.getStr("content");

                String result = String.format("""
                        [工具调用] 写入文件 %s
                        ```%s
                        %s
                        ```
                        """, relativeFilePath, suffix, content);

                // 输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                log.info("处理工具执行消息: toolId={}, filePath={}", toolExecutedMessage.getId(), relativeFilePath);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
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
