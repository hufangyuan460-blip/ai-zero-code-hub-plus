package com.swu.aiZeroCodeHub.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.swu.aiZeroCodeHub.common.ResultUtils;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.model.dto.user.UserAddRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserLoginRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserRegisterRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserUpdateRequest;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.mapper.UserMapper;
import com.swu.aiZeroCodeHub.model.enums.UserRoleEnum;
import com.swu.aiZeroCodeHub.model.vo.user.LoginUserVO;
import com.swu.aiZeroCodeHub.model.vo.user.UserVO;
import com.swu.aiZeroCodeHub.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.swu.aiZeroCodeHub.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author hxyz61
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService{

    private static final long MAX_PAGE_SIZE = 50;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "userAccount",
            "userName",
            "userRole",
            "createTime",
            "updateTime"
    );

    /**
     * 用户注册
     * @param userRegisterRequest
     * @return
     */
    @Override
    public BaseResponse<Long> userRegister(UserRegisterRequest userRegisterRequest) {
        //检查参数
        ThrowUtils.throwExceptionByConditionAndErrorCode(userRegisterRequest==null,ErrorCode.PARAM_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();

        if(StrUtil.hasBlank(userAccount,userPassword,checkPassword)){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"参数为空");
        }
        if(userAccount.length()<4){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"账户过短");
        }
        if(userPassword.length()<8){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"密码过短");
        }
        if(!userPassword.equals(checkPassword)){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"两次密码不一致");
        }

        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount",userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if(count>0){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"账户已存在");
        }

        //加密
        String encryptPassword = encryptPassword(userPassword);
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("none");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if(!saveResult){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"注册失败，数据库异常");
        }


        return ResultUtils.success(user.getId());
    }
    //加密
    private String encryptPassword(String password){
        //盐值
        final String SALT=System.getenv("SALT");
        return DigestUtils.md5DigestAsHex((SALT+password).getBytes());
    }

    /**
     * 用户登陆
     * @param userLoginRequest
     * @return
     */
    @Override
    public BaseResponse<LoginUserVO> userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
       ThrowUtils.throwExceptionByConditionAndErrorCode(userLoginRequest==null,ErrorCode.PARAM_ERROR);
       String userAccount = userLoginRequest.getUserAccount();
       String userPassword = userLoginRequest.getUserPassword();
       if(StrUtil.hasBlank(userAccount,userPassword)){
           throw new BusinessException(ErrorCode.PARAM_ERROR,"参数为空");
       }
        if(userAccount.length()<4){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"账户过短");
        }
        if(userPassword.length()<8){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"密码过短");
        }

        String encryptPassword = encryptPassword(userPassword);
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount",userAccount);
        User user = this.getOne(queryWrapper);
        if(user==null){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"用户不存在");
        }
        if (!encryptPassword.equals(user.getUserPassword())){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"密码错误");
        }

        //设置会话信息
        request.getSession().setAttribute(USER_LOGIN_STATE,user);
        LoginUserVO loginUserVo = getLoginUserVo(user);

        return ResultUtils.success(loginUserVo);
    }

    private LoginUserVO getLoginUserVo(User user){
        if(user==null){
            return null;
        }
        LoginUserVO loginUserVo = new LoginUserVO();
        BeanUtils.copyProperties(user,loginUserVo);
        return loginUserVo;
    }



    /**
     * 获取当前登陆用户（内部调用，返回实体）
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object objectUser = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (objectUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User user = (User) objectUser;
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User currentUser = this.getById(user.getId());
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取当前登陆用户
     * @param request
     * @return
     */
    @Override
    public BaseResponse<LoginUserVO> getCurrentUser(HttpServletRequest request){
        Object objectUser = request.getSession().getAttribute(USER_LOGIN_STATE);
        if(objectUser==null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User user = (User)objectUser;
        if(user==null||user.getId()==null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        long userId=user.getId();
        User currentUser = this.getById(userId);
        if(currentUser==null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        LoginUserVO loginUserVo = getLoginUserVo(currentUser);
        return ResultUtils.success(loginUserVo);

    }




    /**
     * 用户登出
     * @param request
     * @return
     */
    @Override
    public BaseResponse<Boolean> userLogout(HttpServletRequest request){
        Object objectUser = request.getSession().getAttribute(USER_LOGIN_STATE);
        if(objectUser==null){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"登陆失效");
        }

        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return ResultUtils.success(true);

    };


    //用户信息脱敏
    private UserVO getUserVo(User user){
        if(user==null){
            return null;
        }
        UserVO userVo = new UserVO();
        BeanUtils.copyProperties(user,userVo);
        return userVo;
    }
    private List<UserVO> getUserVoList(List<User> users){
        if(CollUtil.isEmpty(users)){
            return new ArrayList<UserVO>();
        }
        return users.stream().map(u->getUserVo(u)).collect(Collectors.toList());
    }

    @Override
    public long addUser(UserAddRequest userAddRequest) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(userAddRequest == null, ErrorCode.PARAM_ERROR);
        String userAccount = userAddRequest.getUserAccount();
        String userPassword = userAddRequest.getUserPassword();
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账户过短");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码过短");
        }

        QueryWrapper existsQuery = new QueryWrapper();
        existsQuery.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(existsQuery);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "账户已存在");
        }

        User user = new User();
        user.setUserAccount(userAccount);
        String userName = userAddRequest.getUserName();
        if (StrUtil.isBlank(userName)) {
            userName = "none";
        }
        user.setUserName(userName);
        user.setUserAvatar(userAddRequest.getUserAvatar());
        user.setUserProfile(userAddRequest.getUserProfile());

        String userRole = userAddRequest.getUserRole();
        if (StrUtil.isBlank(userRole)) {
            userRole = UserRoleEnum.USER.getValue();
        } else if (UserRoleEnum.getEnumByValue(userRole) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户角色错误");
        }
        user.setUserRole(userRole);

        user.setUserPassword(encryptPassword(userPassword));

        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存失败");
        }
        return user.getId();
    }

    @Override
    public boolean deleteUser(long id) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id <= 0, ErrorCode.PARAM_ERROR);
        return this.removeById(id);
    }

    @Override
    public boolean updateUser(UserUpdateRequest userUpdateRequest) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(userUpdateRequest == null, ErrorCode.PARAM_ERROR);
        Long id = userUpdateRequest.getId();
        ThrowUtils.throwExceptionByConditionAndErrorCode(id == null || id <= 0, ErrorCode.PARAM_ERROR);

        User oldUser = this.getById(id);
        if (oldUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        if (StrUtil.isNotBlank(userUpdateRequest.getUserName())) {
            oldUser.setUserName(userUpdateRequest.getUserName());
        }
        if (StrUtil.isNotBlank(userUpdateRequest.getUserAvatar())) {
            oldUser.setUserAvatar(userUpdateRequest.getUserAvatar());
        }
        if (StrUtil.isNotBlank(userUpdateRequest.getUserProfile())) {
            oldUser.setUserProfile(userUpdateRequest.getUserProfile());
        }
        if (StrUtil.isNotBlank(userUpdateRequest.getUserRole())) {
            String userRole = userUpdateRequest.getUserRole();
            if (UserRoleEnum.getEnumByValue(userRole) == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "用户角色错误");
            }
            oldUser.setUserRole(userRole);
        }
        if (StrUtil.isNotBlank(userUpdateRequest.getUserPassword())) {
            String userPassword = userUpdateRequest.getUserPassword();
            if (userPassword.length() < 8) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "密码过短");
            }
            oldUser.setUserPassword(encryptPassword(userPassword));
        }

        return this.updateById(oldUser);
    }

    @Override
    public UserVO getUserVoById(long id) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id <= 0, ErrorCode.PARAM_ERROR);
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        return getUserVo(user);
    }

    @Override
    public List<UserVO> listUserVo(UserQueryRequest userQueryRequest) {
        QueryWrapper queryWrapper = buildUserQueryWrapper(userQueryRequest);
        List<User> userList = this.mapper.selectListByQuery(queryWrapper);
        return getUserVoList(userList);
    }

    @Override
    public Page<UserVO> pageUserVo(UserQueryRequest userQueryRequest) {
        QueryWrapper queryWrapper = buildUserQueryWrapper(userQueryRequest);
        long pageNumber = userQueryRequest == null ? 1 : userQueryRequest.getPageNumber();
        long pageSize = userQueryRequest == null ? 10 : userQueryRequest.getPageSize();
        if (pageNumber < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageNumber 错误");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageSize 错误");
        }

        Page<User> userPage = this.mapper.paginate(pageNumber, pageSize, queryWrapper);
        List<UserVO> userVoRecords = getUserVoList(userPage.getRecords());

        Page<UserVO> userVoPage = new Page<>(userPage.getPageNumber(), userPage.getPageSize());
        userVoPage.setTotalRow(userPage.getTotalRow());
        userVoPage.setRecords(userVoRecords);
        return userVoPage;
    }

    private QueryWrapper buildUserQueryWrapper(UserQueryRequest userQueryRequest) {
        QueryWrapper queryWrapper = new QueryWrapper();
        if (userQueryRequest == null) {
            return queryWrapper;
        }
        Long id = userQueryRequest.getId();
        if (id != null) {
            if (id <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "id 错误");
            }
            queryWrapper.eq("id", id);
        }
        if (StrUtil.isNotBlank(userQueryRequest.getUserAccount())) {
            queryWrapper.eq("userAccount", userQueryRequest.getUserAccount());
        }
        if (StrUtil.isNotBlank(userQueryRequest.getUserName())) {
            queryWrapper.like("userName", userQueryRequest.getUserName());
        }
        if (StrUtil.isNotBlank(userQueryRequest.getUserProfile())) {
            queryWrapper.like("userProfile", userQueryRequest.getUserProfile());
        }
        if (StrUtil.isNotBlank(userQueryRequest.getUserRole())) {
            String userRole = userQueryRequest.getUserRole();
            if (UserRoleEnum.getEnumByValue(userRole) == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "用户角色错误");
            }
            queryWrapper.eq("userRole", userRole);
        }

        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        if (StrUtil.isNotBlank(sortField)) {
            if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "sortField 错误");
            }
            boolean asc = "ascend".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder);
            boolean desc = "descend".equalsIgnoreCase(sortOrder) || "desc".equalsIgnoreCase(sortOrder) || StrUtil.isBlank(sortOrder);
            if (!asc && !desc) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "sortOrder 错误");
            }
            queryWrapper.orderBy(sortField + (asc ? " asc" : " desc"));
        } else {
            queryWrapper.orderBy("id desc");
        }
        return queryWrapper;
    }

}
