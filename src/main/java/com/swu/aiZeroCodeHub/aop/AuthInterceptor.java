package com.swu.aiZeroCodeHub.aop;

import com.swu.aiZeroCodeHub.annotation.AuthCheck;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.model.enums.UserRoleEnum;
import com.swu.aiZeroCodeHub.model.vo.user.LoginUserVO;
import com.swu.aiZeroCodeHub.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuthInterceptor {
    @Resource
    private UserService userService;

    /**
     * 执行拦截
     * @param joinPoint 切入点
     * @param authCheck 权限校验注解 @authCheck(UserConstant.user)
     * @return
     * @throws Throwable
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        //获取注解值 user,admin
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request=((ServletRequestAttributes)requestAttributes).getRequest();

        //获取当前用户
        BaseResponse<LoginUserVO> currentUserResponse = userService.getCurrentUser(request);
        LoginUserVO currentUserVo = currentUserResponse.getData();
        //注解需要权限
        UserRoleEnum mustRoleEnum=UserRoleEnum.getEnumByValue(mustRole);

        //需要权限不存在，放行
        if (mustRoleEnum == null){
            return joinPoint.proceed();
        }

        //以下为：注解明确需要权限，user或admin
        UserRoleEnum userRoleEnum=UserRoleEnum.getEnumByValue(currentUserVo.getUserRole());
        //如果为user,用户登陆即可
        if (userRoleEnum == null){
            //无权限，拒绝
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        //如果要求管理员权限却没有
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum)&&!UserRoleEnum.ADMIN.equals(userRoleEnum)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //放行
        return joinPoint.proceed();





    }
}
