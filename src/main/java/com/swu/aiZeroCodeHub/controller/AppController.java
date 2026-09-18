package com.swu.aiZeroCodeHub.controller;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import com.swu.aiZeroCodeHub.annotation.AuthCheck;
import com.swu.aiZeroCodeHub.common.ResultUtils;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.constant.AppConstant;
import com.swu.aiZeroCodeHub.constant.UserConstant;
import com.swu.aiZeroCodeHub.core.ratelimit.DistributedRateLimiter;
import com.swu.aiZeroCodeHub.core.ratelimit.RateLimitException;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.generation.GenerationEvent;
import com.swu.aiZeroCodeHub.generation.GenerationRunProperties;
import com.swu.aiZeroCodeHub.generation.GenerationRunState;
import com.swu.aiZeroCodeHub.model.dto.app.AppAdminQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppAdminUpdateRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppCreateRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppFeaturedQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppMyQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppUpdateMyRequest;
import com.swu.aiZeroCodeHub.model.dto.generation.GenerationCreateRequest;
import com.swu.aiZeroCodeHub.model.entity.App;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.service.ProjectDownloadService;
import com.swu.aiZeroCodeHub.service.ScreenshotService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import com.swu.aiZeroCodeHub.service.AppService;
import com.swu.aiZeroCodeHub.model.vo.app.AppVO;
import com.swu.aiZeroCodeHub.model.vo.generation.GenerationCreateVO;
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

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.LinkedHashSet;

/**
 * 应用 控制层。
 *
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
    @Autowired
    private ScreenshotService screenshotService;
    @Autowired
    private DistributedRateLimiter distributedRateLimiter;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private GenerationRunProperties generationRunProperties;

    private static final long CHAT_GENERATE_CODE_RATE = 1;
    private static final long CHAT_GENERATE_CODE_INTERVAL_SECONDS = 5;
    /** 旧 GET 适配器只允许很短的提示词，避免把完整 prompt 放进 URL。 */
    private static final int LEGACY_GET_MESSAGE_LIMIT = 2048;

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
                                                          @RequestParam(value = "executionMode", required = false) String executionMode,
                                                          HttpServletRequest request) {
        try {
            if (userMessage != null && userMessage.length() > LEGACY_GET_MESSAGE_LIMIT) {
                return sseError(new BusinessException(ErrorCode.PARAM_ERROR,
                        "用户消息不能超过8000个字符；旧版 GET 入口仅支持 2048 个字符，请使用 POST /app/generation"));
            }
            User loginUser = userService.getLoginUser(request);
            RateLimitException rateLimitException = tryAcquireGenerationRateLimit(loginUser);
            if (rateLimitException != null) return sseError(rateLimitException);
            Flux<GenerationEvent> eventFlux = appService.chatToGenCode(
                    appId, userMessage, codeGenType, executionMode, loginUser);
            return eventFlux.map(this::toSseEvent)
                    .onErrorResume(this::sseError);
        } catch (Throwable throwable) {
            return sseError(throwable);
        }
    }

    /**
     * 创建生成运行。提示词通过 JSON body 传输，runId 始终由服务端生成。
     */
    @PostMapping(value = "/generation", consumes = MediaType.APPLICATION_JSON_VALUE)
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<GenerationCreateVO> createGeneration(@RequestBody GenerationCreateRequest generationRequest,
                                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        RateLimitException rateLimitException = tryAcquireGenerationRateLimit(loginUser);
        if (rateLimitException != null) {
            throw rateLimitException;
        }
        return ResultUtils.success(appService.createGeneration(generationRequest, loginUser));
    }

    /** GET 兼容入口和 POST 新入口共享同一个用户级生成限流桶。 */
    private RateLimitException tryAcquireGenerationRateLimit(User loginUser) {
        // Unit-level legacy callers may construct the controller without Spring wiring;
        // the production bean is always present.
        if (distributedRateLimiter == null) {
            return null;
        }
        if (loginUser == null || loginUser.getId() == null) {
            return new RateLimitException("请求过于频繁，请稍后再试", effectiveRateWindowSeconds());
        }
        long window = effectiveRateWindowSeconds();
        String key = "rate:agent:generation:user:" + loginUser.getId();
        boolean acquired = distributedRateLimiter.tryAcquire(key, effectiveRate(), window);
        return acquired ? null : new RateLimitException("请求过于频繁，请稍后再试", window);
    }

    private long effectiveRate() {
        return generationRunProperties == null
                ? CHAT_GENERATE_CODE_RATE : generationRunProperties.effectiveGenerationRate();
    }

    private long effectiveRateWindowSeconds() {
        return generationRunProperties == null
                ? CHAT_GENERATE_CODE_INTERVAL_SECONDS : generationRunProperties.effectiveGenerationRateWindowSeconds();
    }

    /**
     * 订阅已创建的生成运行。晚到订阅只回放该 run 的事件，不会重新执行。
     */
    @GetMapping(value = "/generation/stream/{runId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public Flux<ServerSentEvent<String>> streamGeneration(@PathVariable String runId,
                                                           @RequestParam(value = "afterSequence", required = false) Long afterSequence,
                                                           HttpServletRequest request) {
        try {
            User loginUser = userService.getLoginUser(request);
            Flux<GenerationEvent> events = afterSequence == null || afterSequence <= 0
                    ? appService.subscribeGeneration(runId, loginUser)
                    : appService.subscribeGeneration(runId, loginUser, afterSequence);
            return events
                    .map(this::toSseEvent)
                    .onErrorResume(this::sseError);
        } catch (Throwable throwable) {
            return sseError(throwable);
        }
    }

    /** 兼容既有 Java 调用方；浏览器重连时使用带 afterSequence 的接口。 */
    public Flux<ServerSentEvent<String>> streamGeneration(String runId, HttpServletRequest request) {
        return streamGeneration(runId, null, request);
    }

    /** 查询生成运行状态，供刷新页面恢复运行信息。 */
    @GetMapping("/generation/{runId}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<GenerationRunState> getGenerationStatus(@PathVariable String runId,
                                                                 HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.getGenerationStatus(runId, loginUser));
    }

    /**
     * 协作式取消生成任务。断开 SSE 不会触发该接口。
     */
    @PostMapping("/generation/cancel/{runId}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> cancelGeneration(@PathVariable String runId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.cancelGeneration(runId, loginUser));
    }

    /**
     * 兼容旧的 Java 调用方；HTTP 请求统一使用带 executionMode 的映射方法。
     */
    public Flux<ServerSentEvent<String>> chatGenerateCode(Long appId,
                                                          String userMessage,
                                                          String codeGenType,
                                                          HttpServletRequest request) {
        return chatGenerateCode(appId, userMessage, codeGenType, null, request);
    }

    /**
     * SSE 序列化的唯一入口。业务层和工作流不拼接 event/data 文本。
     */
    private ServerSentEvent<String> toSseEvent(GenerationEvent event) {
        String data = event.data() instanceof String stringData
                ? stringData
                : serializeEventData(event.data());
        ServerSentEvent.Builder<String> builder = ServerSentEvent.builder(data).event(event.type());
        if (event.sequence() > 0) {
            builder.id(String.valueOf(event.sequence()));
        }
        return builder.build();
    }

    private String serializeEventData(Object data) {
        if (data == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{\"message\":\"事件数据序列化失败\"}";
        }
    }

    private Flux<ServerSentEvent<String>> sseError(Throwable throwable) {
        int code = ErrorCode.SYSTEM_ERROR.getCode();
        String message = "系统内部异常";
        String page = null;
        if (throwable instanceof RateLimitException rateLimitException) {
            code = ErrorCode.RATE_LIMIT_ERROR.getCode();
            message = rateLimitException.getMessage();
            page = "<html><body><h2>请求过于频繁</h2><p>请在 " + rateLimitException.getRetryAfterSeconds() + " 秒后重试</p></body></html>";
        } else if (throwable instanceof dev.langchain4j.guardrail.GuardrailException) {
            code = ErrorCode.FORBIDDEN_ERROR.getCode();
            message = "检测到不安全的提示词输入，已拦截";
            page = "<html><body><h2>请求已拦截</h2><p>检测到不安全的提示词输入，请修改后重试</p></body></html>";
        } else if (throwable instanceof BusinessException businessException) {
            code = businessException.getCode();
            message = safeSseMessage(businessException.getMessage(), "请求处理失败");
        } else if (throwable != null && StrUtil.isNotBlank(throwable.getMessage())) {
            message = safeSseMessage(throwable.getMessage(), "生成失败，请稍后重试");
        }
        String json;
        try {
            Map<String, Object> errorData = new java.util.LinkedHashMap<>();
            errorData.put("code", code);
            errorData.put("message", message);
            errorData.put("page", page);
            json = objectMapper.writeValueAsString(errorData);
        } catch (Exception e) {
            json = "{\"code\":" + code + ",\"message\":\"请求处理失败\"}";
        }
        ServerSentEvent<String> error = ServerSentEvent.builder(json).event("generation_error").build();
        ServerSentEvent<String> done = ServerSentEvent.builder("").event("done").build();
        return Flux.just(error, done);
    }

    private String safeSseMessage(String value, String fallback) {
        if (StrUtil.isBlank(value)) {
            return fallback;
        }
        String sanitized = value
                .replaceAll("(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*[^,;\\s]+", "$1=***")
                .replaceAll("[A-Za-z]:\\\\[^\\n\\r]*|/(?:[^\\n\\r ]+/)+[^\\n\\r ]*", "[路径]");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
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

    @PostMapping("/screenshot")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<String> screenshot(@RequestBody java.util.Map<String, String> body, HttpServletRequest request) {
        String appIdStr = body.get("appId");
        String webUrl = body.get("webUrl");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appIdStr == null, ErrorCode.PARAM_ERROR, "应用ID不能为空");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(StrUtil.isBlank(webUrl), ErrorCode.PARAM_ERROR, "网页URL不能为空");
        Long appId = Long.valueOf(appIdStr);

        App app = appService.getById(appId);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该应用");
        }

        String fullUrl = webUrl;
        if (StrUtil.isNotBlank(webUrl) && !webUrl.startsWith("http://") && !webUrl.startsWith("https://")) {
            String path = webUrl.startsWith("/") ? webUrl : "/" + webUrl;
            String scheme = request.getScheme();
            String host = request.getServerName();
            int port = request.getServerPort();
            boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443);
            String portPart = defaultPort ? "" : ":" + port;
            fullUrl = scheme + "://" + host + portPart + path;
        }
        String coverUrl = screenshotService.generateAndUploadScreenshot(appId, fullUrl);
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setCover(coverUrl);
        updateApp.setEditTime(LocalDateTime.now());
        appService.updateById(updateApp);
        return ResultUtils.success(coverUrl);
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
        ensureAppDeployed(app);

        File sourceDir = resolveCodeOutputDir(appId, app.getCodeGenType());
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(sourceDir == null,
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
        ensureAppDeployed(app);
        String token = request.getParameter("token");
        if (StrUtil.isNotBlank(token)) {
            boolean valid = projectDownloadService.validateDownloadToken(token, appId, loginUser.getId());
            ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(!valid, ErrorCode.NO_AUTH_ERROR, "下载链接已失效，请重新获取");
        }

        // 4. 构建应用代码目录路径（生成目录，非部署目录）
        // 5. 检查代码目录是否存在
        File sourceDir = resolveCodeOutputDir(appId, app.getCodeGenType());
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(sourceDir == null,
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");

        // 6. 生成下载文件名（不建议添加中文内容）
        String downloadFileName = String.valueOf(appId);

        // 7. 调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDir.getAbsolutePath(), downloadFileName, request, response);
    }

    /**
     * 下载必须建立在应用成功部署的状态之上，避免仅凭代码生成目录绕过前端限制。
     */
    private void ensureAppDeployed(App app) {
        boolean notDeployed = app == null
                || StrUtil.isBlank(app.getDeployKey())
                || app.getDeployedTime() == null;
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(notDeployed,
                ErrorCode.FORBIDDEN_ERROR, "应用尚未成功部署，部署完成后才能下载源码");
    }

    private File resolveCodeOutputDir(Long appId, String preferredType) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(preferredType)) {
            types.add(preferredType);
        }
        types.add(CodeGenTypeEnum.VUE_PROJECT.getValue());
        types.add(CodeGenTypeEnum.MULTI_FILE.getValue());
        types.add(CodeGenTypeEnum.HTML.getValue());
        for (String type : types) {
            String sourceDirName = type + "_" + appId;
            String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
            File sourceDir = new File(sourceDirPath);
            if (sourceDir.exists() && sourceDir.isDirectory()) {
                return sourceDir;
            }
        }
        return null;
    }


}


   
