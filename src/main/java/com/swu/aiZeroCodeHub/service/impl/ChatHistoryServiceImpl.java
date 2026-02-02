package com.swu.aiZeroCodeHub.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.mapper.AppMapper;
import com.swu.aiZeroCodeHub.mapper.ChatHistoryMapper;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryAddRequest;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryQueryRequest;
import com.swu.aiZeroCodeHub.model.entity.App;
import com.swu.aiZeroCodeHub.model.entity.ChatHistory;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.enums.MessageTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ChatHistoryVO;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话历史服务实现
 */
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    private AppMapper appMapper;

    @Override
    public long addChatHistory(ChatHistoryAddRequest chatHistoryAddRequest, User loginUser) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(chatHistoryAddRequest == null, ErrorCode.PARAM_ERROR);
        Long appId = chatHistoryAddRequest.getAppId();
        Integer messageType = chatHistoryAddRequest.getMessageType();
        String content = chatHistoryAddRequest.getContent();

        // 校验参数
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用不存在");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(MessageTypeEnum.getEnumByValue(messageType) == null, ErrorCode.PARAM_ERROR, "消息类型错误");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(StrUtil.isBlank(content), ErrorCode.PARAM_ERROR, "消息内容不能为空");

        // 校验应用是否存在
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 创建对话历史
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setAppId(appId);
        chatHistory.setUserId(loginUser.getId());
        chatHistory.setMessageType(messageType);
        chatHistory.setContent(content);
        // Mybatis-Flex 会自动填充 createTime, updateTime，这里显式设置一下也可以，或者依赖数据库/Listener
        // 假设数据库或Entity配置了自动填充，如果没有，需手动设置。查看AppServiceImpl，save时未设置createTime，说明有自动填充或DB默认值。
        // 但这里为了保险，或者遵循项目习惯，查看App实体有createTime字段。AppServiceImpl save时没有setCreateTime。
        // 假设DB有默认值或GlobalConfig。
        
        boolean result = this.save(chatHistory);
        ThrowUtils.throwExceptionByConditionAndErrorCode(!result, ErrorCode.OPERATION_ERROR);
        return chatHistory.getId();
    }

    @Override
    public Page<ChatHistoryVO> listChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest, User loginUser) {
        if (chatHistoryQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Long appId = chatHistoryQueryRequest.getAppId();
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用id不能为空");

        // 校验应用是否存在及权限
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        
        // Allow access if:
        // 1. User is the creator
        // 2. User is admin
        // 3. App is featured (priority > 0)
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        boolean isAdmin = "admin".equals(loginUser.getUserRole());
        boolean isFeatured = app.getPriority() != null && app.getPriority() > 0;

        if (!isCreator && !isAdmin && !isFeatured) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看");
        }

        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("appId", appId);
        if (chatHistoryQueryRequest.getMessageType() != null) {
            queryWrapper.eq("messageType", chatHistoryQueryRequest.getMessageType());
        }
        
        // 游标分页逻辑：如果提供了 lastCreateTime，则查询早于该时间的记录
        if (chatHistoryQueryRequest.getLastCreateTime() != null) {
            queryWrapper.lt("createTime", chatHistoryQueryRequest.getLastCreateTime());
        }
        
        // 按时间倒序，获取最新消息
        queryWrapper.orderBy("createTime", false); // false for desc

        long pageNumber = chatHistoryQueryRequest.getPageNumber();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        
        Page<ChatHistory> page = this.page(new Page<>(pageNumber, pageSize), queryWrapper);
        return getChatHistoryVOPage(page);
    }

    @Override
    public Page<ChatHistoryVO> listAllChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = new QueryWrapper();
        if (chatHistoryQueryRequest != null) {
            if (chatHistoryQueryRequest.getAppId() != null) {
                queryWrapper.eq("appId", chatHistoryQueryRequest.getAppId());
            }
            if (chatHistoryQueryRequest.getUserId() != null) {
                queryWrapper.eq("userId", chatHistoryQueryRequest.getUserId());
            }
            if (chatHistoryQueryRequest.getMessageType() != null) {
                queryWrapper.eq("messageType", chatHistoryQueryRequest.getMessageType());
            }
        }
        // 管理员查看，按时间倒序
        queryWrapper.orderBy("createTime", false);

        long pageNumber = chatHistoryQueryRequest == null ? 1 : chatHistoryQueryRequest.getPageNumber();
        long pageSize = chatHistoryQueryRequest == null ? 10 : chatHistoryQueryRequest.getPageSize();

        Page<ChatHistory> page = this.page(new Page<>(pageNumber, pageSize), queryWrapper);
        return getChatHistoryVOPage(page);
    }

    @Override
    public boolean deleteChatHistoryByAppId(Long appId) {
        if (appId == null || appId <= 0) {
            return false;
        }
        // 逻辑删除该应用下的所有对话历史
        // Mybatis-Flex 的 logic delete 通常是在 remove 时生效。
        // 但 remove(QueryWrapper) 会执行 delete where ...
        // 如果配置了 logic delete，remove(QueryWrapper) 会变成 update is_delete=1 where ...
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("appId", appId);
        return this.remove(queryWrapper);
    }

    /**
     * 分页对象转换
     *
     * @param page
     * @return
     */
    private Page<ChatHistoryVO> getChatHistoryVOPage(Page<ChatHistory> page) {
        List<ChatHistory> records = page.getRecords();
        Page<ChatHistoryVO> voPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        if (CollUtil.isEmpty(records)) {
            voPage.setRecords(new ArrayList<>());
            return voPage;
        }
        List<ChatHistoryVO> voList = new ArrayList<>();
        for (ChatHistory chatHistory : records) {
            ChatHistoryVO vo = new ChatHistoryVO();
            BeanUtils.copyProperties(chatHistory, vo);
            voList.add(vo);
        }
        voPage.setRecords(voList);
        return voPage;
    }


}
