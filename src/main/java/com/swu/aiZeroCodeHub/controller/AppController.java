package com.swu.aiZeroCodeHub.controller;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.swu.aiZeroCodeHub.annotation.AuthCheck;
import com.swu.aiZeroCodeHub.common.ResultUtils;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.constant.AppConstant;
import com.swu.aiZeroCodeHub.constant.UserConstant;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.model.dto.app.AppAdminQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppAdminUpdateRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppCreateRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppFeaturedQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppMyQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppUpdateMyRequest;
import com.swu.aiZeroCodeHub.model.entity.App;
import com.swu.aiZeroCodeHub.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import com.swu.aiZeroCodeHub.service.AppService;
import com.swu.aiZeroCodeHub.model.vo.app.AppVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import com.swu.aiZeroCodeHub.service.UserService;
import com.swu.aiZeroCodeHub.model.entity.User;
import reactor.core.publisher.Mono;

import java.io.File;

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
    @Autowired
    private ProjectDownloadService projectDownloadService;

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
     * 流式生成代码 / 普通对话（SSE）
     * 前端使用 EventSource 订阅该接口
     */
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public Flux<ServerSentEvent<String>> chatGenerateCode(@RequestParam("appId") Long appId,
                                                          @RequestParam("userMessage") String userMessage,
                                                          @RequestParam(value = "codeGenType", required = false) String codeGenType,
                                                          HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Flux<String> flux = appService.chatToGenCode(appId, userMessage, codeGenType, loginUser);
        Flux<ServerSentEvent<String>> stream = flux.map(chunk ->
                ServerSentEvent.builder(chunk).build()
        );
        return stream.concatWith(Flux.just(ServerSentEvent.builder("")
                .event("done")
                .build()));
    }

    /**
     * 部署生成的应用，返回部署地址
     */
    @PostMapping("/deploy")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<String> deploy(@RequestBody java.util.Map<String, String> body, HttpServletRequest request) {
        String appIdStr = body.get("appId");
        ThrowUtils.throwExceptionByConditionAndErrorCode(appIdStr == null, ErrorCode.PARAM_ERROR);
        Long appId = Long.valueOf(appIdStr);
        User loginUser = userService.getLoginUser(request);
        String url = appService.deployApp(appId, loginUser);
        return ResultUtils.success(url);
    }

    /**
     * 获取下载链接（带时效）
     * @param appId 应用ID
     * @param request 请求
     * @return 下载链接
     */
    @GetMapping("/download/link/{appId}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<String> getDownloadLink(@PathVariable Long appId, HttpServletRequest request) {
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用ID无效");

        App app = appService.getById(appId);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载该应用代码");
        }

        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");

        String token = projectDownloadService.createDownloadToken(appId, loginUser.getId());
        String downloadUrl = String.format("/app/download/%s?token=%s", appId, token);
        return ResultUtils.success(downloadUrl);
    }



    /**
     * 下载应用代码
     * @param appId 应用ID
     * @param request 请求
     * @param response 响应
     */
    @GetMapping("/download/{appId}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public void downloadAppCode(@PathVariable Long appId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        // 1. 基础校验
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用ID无效");

        // 2. 查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3. 权限校验：只有应用创建者可以下载代码
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载该应用代码");
        }
        String token = request.getParameter("token");
        if (StrUtil.isNotBlank(token)) {
            boolean valid = projectDownloadService.validateDownloadToken(token, appId, loginUser.getId());
            ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(!valid, ErrorCode.NO_AUTH_ERROR, "下载链接已失效，请重新获取");
        }

        // 4. 构建应用代码目录路径（生成目录，非部署目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;

        // 5. 检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");

        // 6. 生成下载文件名（不建议添加中文内容）
        String downloadFileName = String.valueOf(appId);

        // 7. 调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, request, response);
    }


}


   
