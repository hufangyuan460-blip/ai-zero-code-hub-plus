package com.swu.aiZeroCodeHub.controller;

import com.mybatisflex.core.paginate.Page;
import com.swu.aiZeroCodeHub.annotation.AuthCheck;
import com.swu.aiZeroCodeHub.common.ResultUtils;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.constant.UserConstant;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.model.dto.user.UserAddRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserLoginRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserRegisterRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserUpdateRequest;
import com.swu.aiZeroCodeHub.model.vo.user.LoginUserVO;
import com.swu.aiZeroCodeHub.model.vo.user.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.swu.aiZeroCodeHub.service.UserService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 用户 控制层。
 *
 * @author hxyz61
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 保存用户。
     *
     * @param userAddRequest 用户
     * @return 新用户 id
     */
    @PostMapping("/save")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> save(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(userAddRequest == null, ErrorCode.PARAM_ERROR);
        long userId = userService.addUser(userAddRequest);
        return ResultUtils.success(userId);
    }

    /**
     * 根据主键删除用户。
     *
     * @param id 主键
     * @return 删除结果
     */
    @DeleteMapping("/remove/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> remove(@PathVariable Long id) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id == null || id <= 0, ErrorCode.PARAM_ERROR);
        boolean result = userService.deleteUser(id);
        return ResultUtils.success(result);
    }

    /**
     * 根据主键更新用户。
     *
     * @param userUpdateRequest 用户
     * @return 更新结果
     */
    @PutMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> update(@RequestBody UserUpdateRequest userUpdateRequest) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(userUpdateRequest == null, ErrorCode.PARAM_ERROR);
        boolean result = userService.updateUser(userUpdateRequest);
        return ResultUtils.success(result);
    }

    /**
     * 查询所有用户。
     *
     * @return 所有数据
     */
    @GetMapping("/list")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<UserVO>> list(UserQueryRequest userQueryRequest) {
        List<UserVO> userVoList = userService.listUserVo(userQueryRequest);
        return ResultUtils.success(userVoList);
    }

    /**
     * 根据主键获取用户。
     *
     * @param id 用户主键
     * @return 用户详情
     */
    @GetMapping("/getInfo/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<UserVO> getInfo(@PathVariable Long id) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id == null || id <= 0, ErrorCode.PARAM_ERROR);
        UserVO userVo = userService.getUserVoById(id);
        return ResultUtils.success(userVo);
    }

    /**
     * 分页查询用户。
     *
     * @param userQueryRequest 分页对象
     * @return 分页对象
     */
    @GetMapping("/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> page(UserQueryRequest userQueryRequest) {
        Page<UserVO> page = userService.pageUserVo(userQueryRequest);
        return ResultUtils.success(page);
    }

    /**
     * 用户注册
     * @param userRegisterRequest
     * @return 注册结果
     */
    @PostMapping("/register")
    public BaseResponse<Long> register(@RequestBody UserRegisterRequest userRegisterRequest) {
        return userService.userRegister(userRegisterRequest);
    }

    /**
     * 用户登陆
     * @param userLoginRequest
     * @param request
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> login(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        return userService.userLogin(userLoginRequest, request);
    }

    /**
     * 获取当前登陆用户
     * @param request
     * @return
     */
    @GetMapping("/get/currentUser")
    public BaseResponse<LoginUserVO> getCurrentUser(HttpServletRequest request) {
        return userService.getCurrentUser(request);
    }

    /**
     *  用户登出
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> logout(HttpServletRequest request) {
        return userService.userLogout(request);
    }

}
