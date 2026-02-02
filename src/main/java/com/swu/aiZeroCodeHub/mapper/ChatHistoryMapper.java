package com.swu.aiZeroCodeHub.mapper;

import com.mybatisflex.core.BaseMapper;
import com.swu.aiZeroCodeHub.model.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话历史 Mapper
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {
}
