package com.swu.aiZeroCodeHub.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.swu.aiZeroCodeHub.common.ResultUtils;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.model.dto.user.UserLoginRequest;
import com.swu.aiZeroCodeHub.model.dto.user.UserRegisterRequest;
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
import java.util.stream.Collectors;

import static com.swu.aiZeroCodeHub.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author hxyz61
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService{

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

}
