package com.swu.aiZeroCodeHub.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryAddRequest;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryQueryRequest;
import com.swu.aiZeroCodeHub.model.entity.ChatHistory;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.vo.chatHistory.ChatHistoryVO;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

/**
 * 对话历史服务接口
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    int MAX_MEMORY_MESSAGES = 20;
    int MAX_USER_MESSAGE_LENGTH = 8_000;
    int MAX_AI_HISTORY_LENGTH = 12_000;
    String OVERSIZED_AI_HISTORY_PLACEHOLDER = "AI 回复过长，完整结果已写入项目文件";

    /**
     * 添加对话历史
     *
     * @param chatHistoryAddRequest
     * @param loginUser
     * @return
     */
    long addChatHistory(ChatHistoryAddRequest chatHistoryAddRequest, User loginUser);

    /**
     * 分页获取对话历史（用户）
     *
     * @param chatHistoryQueryRequest
     * @param loginUser
     * @return
     */
    Page<ChatHistoryVO> listChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest, User loginUser);

    /**
     * 分页获取所有对话历史（管理员）
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    Page<ChatHistoryVO> listAllChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 根据应用id删除对话历史
     *
     * @param appId
     * @return
     */
    boolean deleteChatHistoryByAppId(Long appId);

    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory,int maxCount);



}
