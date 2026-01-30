package com.swu.aiZeroCodeHub.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.swu.aiZeroCodeHub.annotation.AuthCheck;
import com.swu.aiZeroCodeHub.common.ResultUtils;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.constant.UserConstant;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.model.dto.app.*;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.vo.user.LoginUserVO;
import com.swu.aiZeroCodeHub.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.swu.aiZeroCodeHub.service.AppService;
import com.swu.aiZeroCodeHub.model.vo.app.AppVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 应用 控制层。
 *
 * @author hxyz61
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Autowired
    private AppService appService;

    @Autowired
    private UserService userService;

    /**
     * 用户创建应用（必须填写 initPrompt）。
     */
    @PostMapping("/create")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Long> create(@RequestBody AppCreateRequest appCreateRequest, HttpServletRequest request) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(appCreateRequest == null, ErrorCode.PARAM_ERROR);
        long appId = appService.createApp(appCreateRequest, request);
        return ResultUtils.success(appId);
    }

    /**
     * 用户根据 id 修改自己的应用（目前只支持修改应用名称）。
     */
    @PutMapping("/my/update")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> updateMy(@RequestBody AppUpdateMyRequest appUpdateMyRequest, HttpServletRequest request) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(appUpdateMyRequest == null, ErrorCode.PARAM_ERROR);
        boolean result = appService.updateMyApp(appUpdateMyRequest, request);
        return ResultUtils.success(result);
    }

    /**
     * 用户根据 id 删除自己的应用。
     */
    @DeleteMapping("/my/remove/{id}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> removeMy(@PathVariable Long id, HttpServletRequest request) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id == null || id <= 0, ErrorCode.PARAM_ERROR);
        boolean result = appService.deleteMyApp(id, request);
        return ResultUtils.success(result);
    }

    /**
     * 用户根据 id 查看应用详情。
     */
    @GetMapping("/my/getInfo/{id}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<AppVO> getMyInfo(@PathVariable Long id, HttpServletRequest request) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id == null || id <= 0, ErrorCode.PARAM_ERROR);
        AppVO appVo = appService.getMyAppVoById(id, request);
        return ResultUtils.success(appVo);
    }

    /**
     * 分页查询自己的应用列表（支持根据名称查询，每页最多 20 个）。
     */
    @GetMapping("/my/page")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Page<AppVO>> pageMy(AppMyQueryRequest appMyQueryRequest, HttpServletRequest request) {
        Page<AppVO> page = appService.pageMyAppVo(appMyQueryRequest, request);
        return ResultUtils.success(page);
    }

    /**
     * 分页查询精选的应用列表（支持根据名称查询，每页最多 20 个）。
     */
    @GetMapping("/featured/page")
    public BaseResponse<Page<AppVO>> pageFeatured(AppFeaturedQueryRequest appFeaturedQueryRequest) {
        Page<AppVO> page = appService.pageFeaturedAppVo(appFeaturedQueryRequest);
        return ResultUtils.success(page);
    }

    /**
     * 管理员根据 id 删除任意应用。
     */
    @DeleteMapping("/admin/remove/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> adminRemove(@PathVariable Long id) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id == null || id <= 0, ErrorCode.PARAM_ERROR);
        boolean result = appService.adminDeleteApp(id);
        return ResultUtils.success(result);
    }

    /**
     * 管理员根据 id 更新任意应用（支持更新应用名称、应用封面、优先级）。
     */
    @PutMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> adminUpdate(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(appAdminUpdateRequest == null, ErrorCode.PARAM_ERROR);
        boolean result = appService.adminUpdateApp(appAdminUpdateRequest);
        return ResultUtils.success(result);
    }

    /**
     * 管理员根据 id 查看应用详情。
     */
    @GetMapping("/admin/getInfo/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> adminGetInfo(@PathVariable Long id) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id == null || id <= 0, ErrorCode.PARAM_ERROR);
        AppVO appVo = appService.adminGetAppVoById(id);
        return ResultUtils.success(appVo);
    }

    /**
     * 管理员分页查询应用列表（支持根据除时间外的任何字段查询，每页数量不限）。
     */
    @GetMapping("/admin/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> adminPage(AppAdminQueryRequest appAdminQueryRequest) {
        Page<AppVO> page = appService.adminPageAppVo(appAdminQueryRequest);
        return ResultUtils.success(page);
    }

    /**
     * 应用聊天生成代码(流式)
     * @param appId 应用ID
     * @param userMessage 用户信息
     * @param request 请求对象
     * @return 生成结果流
     */
    @GetMapping(value = "/chat/gen/code",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId, @RequestParam String userMessage, HttpServletRequest request) {
        //参数校验
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId == null||appId<=0, ErrorCode.PARAM_ERROR,"应用ID无效");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(StrUtil.isBlank(userMessage), ErrorCode.PARAM_ERROR,"用户提示词不能为空");

        BaseResponse<LoginUserVO> currentUserResponse = userService.getCurrentUser(request);
        LoginUserVO data = currentUserResponse.getData();
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(data == null || data.getId() == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        User currentUser = new User();
        currentUser.setId(data.getId());
        currentUser.setUserRole(data.getUserRole());
        Flux<String> contentFlux=appService.chatToGenCode(appId, userMessage, currentUser);

        ServerSentEvent<String> doneEvent = ServerSentEvent.<String>builder()
                .event("done")
                .data("")
                .build();

        return contentFlux
                .map(chunk->{
                    //将内容包装成JSON对象 1解决前端空格丢失问题
                    Map<String,String> wrapper=new HashMap<>();
                    wrapper.put("content", chunk == null ? "" : chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        //发送结束事件 2解决前端难以区分后端是正常响应数据还是异常中断问题
                        doneEvent
                ))
                .onErrorResume(e -> {
                    Map<String, String> errorWrapper = new HashMap<>();
                    errorWrapper.put("message", "生成失败");
                    ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
                            .event("error")
                            .data(JSONUtil.toJsonStr(errorWrapper))
                            .build();
                    return Mono.just(errorEvent).concatWith(Mono.just(doneEvent));
                });


    }


    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest,HttpServletRequest request) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(appDeployRequest == null, ErrorCode.PARAM_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId==null||appId<=0,ErrorCode.PARAM_ERROR,"应用ID不能为空");

        BaseResponse<LoginUserVO> currentUserResponse = userService.getCurrentUser(request);
        LoginUserVO data = currentUserResponse.getData();
        User currentUser=new User();
        BeanUtils.copyProperties(data,currentUser);
        String deployUrl = appService.deployApp(appId, currentUser);
        return ResultUtils.success(deployUrl);
    }
}
