package com.swu.aiZeroCodeHub.controller;

import com.mybatisflex.core.paginate.Page;
import com.swu.aiZeroCodeHub.annotation.AuthCheck;
import com.swu.aiZeroCodeHub.common.ResultUtils;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.constant.UserConstant;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryAddRequest;
import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryQueryRequest;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.vo.ChatHistoryVO;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import com.swu.aiZeroCodeHub.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话历史接口
 */
@RestController
@RequestMapping("/chat/history")
@Slf4j
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;

    // region 增删改查

    /**
     * 创建对话历史（仅测试用，实际对话在 Chat 流程中自动保存）
     *
     * @param chatHistoryAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChatHistory(@RequestBody ChatHistoryAddRequest chatHistoryAddRequest, HttpServletRequest request) {
        if (chatHistoryAddRequest == null) {
            ThrowUtils.throwExceptionByConditionAndErrorCode(true, ErrorCode.PARAM_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long newChatHistoryId = chatHistoryService.addChatHistory(chatHistoryAddRequest, loginUser);
        return ResultUtils.success(newChatHistoryId);
    }

    /**
     * 分页获取对话历史（用户，需传 appId）
     *
     * @param chatHistoryQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<ChatHistoryVO>> listChatHistoryByPage(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest,
                                                                   HttpServletRequest request) {
        if (chatHistoryQueryRequest == null) {
            ThrowUtils.throwExceptionByConditionAndErrorCode(true, ErrorCode.PARAM_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistoryVO> chatHistoryVOPage = chatHistoryService.listChatHistoryByPage(chatHistoryQueryRequest, loginUser);
        return ResultUtils.success(chatHistoryVOPage);
    }

    /**
     * 分页获取所有对话历史（管理员）
     *
     * @param chatHistoryQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistoryVO>> listAllChatHistoryByPage(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest,
                                                                      HttpServletRequest request) {
        Page<ChatHistoryVO> chatHistoryVOPage = chatHistoryService.listAllChatHistoryByPage(chatHistoryQueryRequest);
        return ResultUtils.success(chatHistoryVOPage);
    }

    // endregion
}
