package com.swu.aiZeroCodeHub.service;

import com.mybatisflex.core.service.IService;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.model.dto.user.UserLoginRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserRegisterRequest;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.vo.user.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户 服务层。
 *
 * @author hxyz61
 */
public interface UserService extends IService<User> {
    /**
     * 用户注册
     * @param userRegisterRequest
     * @return
     */
    BaseResponse<Long> userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 用户登陆
     * @param userLoginRequest
     * @return
     */
    BaseResponse<LoginUserVO> userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 获取当前登陆用户
     * @param request
     * @return
     */
    BaseResponse<LoginUserVO> getCurrentUser(HttpServletRequest request);

    /**
     * 用户登出
     * @param request
     * @return
     */
    BaseResponse<Boolean> userLogout(HttpServletRequest request);









}

