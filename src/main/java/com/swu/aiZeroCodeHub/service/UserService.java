package com.swu.aiZeroCodeHub.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.model.dto.user.UserAddRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserLoginRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserRegisterRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserUpdateRequest;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.vo.user.LoginUserVO;
import com.swu.aiZeroCodeHub.model.vo.user.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

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
     * 获取当前登陆用户（内部调用，返回实体）
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

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

    long addUser(UserAddRequest userAddRequest);

    boolean deleteUser(long id);

    boolean updateUser(UserUpdateRequest userUpdateRequest);

    UserVO getUserVoById(long id);

    List<UserVO> listUserVo(UserQueryRequest userQueryRequest);

    Page<UserVO> pageUserVo(UserQueryRequest userQueryRequest);

}
