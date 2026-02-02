package com.swu.aiZeroCodeHub.model.dto.chathistory;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建对话历史请求
 */
@Data
public class ChatHistoryAddRequest implements Serializable {

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 消息类型（0-User, 1-AI）
     */
    private Integer messageType;

    /**
     * 消息内容
     */
    private String content;

    private static final long serialVersionUID = 1L;
}
