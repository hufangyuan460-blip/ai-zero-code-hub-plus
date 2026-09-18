package com.swu.aiZeroCodeHub.core.streamHandler;


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
import com.swu.aiZeroCodeHub.generation.GenerationCancelledException;
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
import java.util.function.BooleanSupplier;

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
        return handle(originFlux, chatHistoryService, appId, loginUser, () -> false);
    }

    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               Long appId,
                               User loginUser,
                               BooleanSupplier cancellationChecker) {
        return handle(originFlux, chatHistoryService, appId, loginUser, cancellationChecker, () -> true);
    }

    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               Long appId,
                               User loginUser,
                               BooleanSupplier cancellationChecker,
                               BooleanSupplier toolBudgetChecker) {
        // 收集数据用于生成后端记忆格式
        HistoryContentAccumulator historyAccumulator = new HistoryContentAccumulator();
        Set<String> seenToolIds = new HashSet<>();

        return originFlux
                .flatMap(chunk -> {
                    throwIfCancelled(cancellationChecker);
                    // 使用SSE解析器处理
                    List<String> jsonMessages = sseParser.parseSSE(chunk);
                    return Flux.fromIterable(jsonMessages)
                            .map(json -> {
                                throwIfCancelled(cancellationChecker);
                                return handleJsonMessageChunk(json, historyAccumulator, seenToolIds, toolBudgetChecker);
                            })
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
                    if (error instanceof GenerationCancelledException) {
                        return;
                    }
                    log.error("处理SSE流失败，类型={}", error.getClass().getSimpleName());
                    saveChatHistory(appId, "AI回复失败: " + error.getMessage(),
                            ChatHistoryMessageTypeEnum.AI, loginUser,chatHistoryService);
                });
    }

    private void throwIfCancelled(BooleanSupplier cancellationChecker) {
        if (cancellationChecker != null && cancellationChecker.getAsBoolean()) {
            throw new com.swu.aiZeroCodeHub.generation.GenerationCancelledException();
        }
    }

    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk,
                                          HistoryContentAccumulator historyAccumulator,
                                          Set<String> seenToolIds,
                                          BooleanSupplier toolBudgetChecker) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());

        // 防止空指针（如果 typeEnum 为 null）
        if (typeEnum == null) {
            return "";
        }

        // 历史内容统一经过净化：Vue 工具参数和完整源码只用于前端展示，不进入历史。
        historyAccumulator.append(HistoryContentSanitizer.sanitize(chunk, com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum.VUE_PROJECT));

        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
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
                if (toolBudgetChecker != null && !toolBudgetChecker.getAsBoolean()) {
                    throw new com.swu.aiZeroCodeHub.exception.BusinessException(
                            com.swu.aiZeroCodeHub.exception.ErrorCode.OPERATION_ERROR, "工具调用预算已用尽");
                }
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = StrUtil.isBlank(toolExecutedMessage.getArguments())
                        ? JSONUtil.createObj()
                        : JSONUtil.parseObj(toolExecutedMessage.getArguments());
                
                // 仅解析路径字段；content、oldContent、newContent 永不进入前端事件或历史。
                String toolName = toolExecutedMessage.getName();
                String path = "";
                
                if (jsonObject.containsKey("relativeFilePath")) {
                    path = jsonObject.getStr("relativeFilePath");
                } else if (jsonObject.containsKey("relativeDirPath")) {
                    path = jsonObject.getStr("relativeDirPath");
                }
                
                String result = "";
                if ("writeFile".equals(toolName) || "modifyFile".equals(toolName)) {
                     result = String.format("[工具调用] %s %s", toolName, path);
                } else if ("readFile".equals(toolName)) {
                     result = String.format("[工具调用] 读取文件 %s", path);
                } else if ("deleteFile".equals(toolName)) {
                     result = String.format("[工具调用] 删除文件 %s", path);
                } else if ("getProjectFileTree".equals(toolName)) {
                     result = String.format("[工具调用] 获取目录结构 %s", path);
                } else {
                     result = String.format("[工具调用] %s", toolName);
                }

                // 前端和历史都只展示工具名称及目标路径，不暴露完整工具参数。
                String output = String.format("\n\n%s\n\n", result);
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
            log.error("保存对话历史失败: appId={}, type={}, reason={}", appId, messageTypeEnum.getText(),
                    e.getClass().getSimpleName());
        }
    }
}
