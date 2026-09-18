package com.swu.aiZeroCodeHub.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.swu.aiZeroCodeHub.constant.AppConstant;
import com.swu.aiZeroCodeHub.constant.UserConstant;
import com.swu.aiZeroCodeHub.core.builder.VueProjectBuilder;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.generation.GenerationDispatcher;
import com.swu.aiZeroCodeHub.generation.GenerationEvent;
import com.swu.aiZeroCodeHub.generation.GenerationRequest;
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
import com.swu.aiZeroCodeHub.mapper.AppMapper;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;
import com.swu.aiZeroCodeHub.model.vo.app.AppVO;
import com.swu.aiZeroCodeHub.model.vo.generation.GenerationCreateVO;
import com.swu.aiZeroCodeHub.service.AppService;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import com.swu.aiZeroCodeHub.service.UserService;
import com.swu.aiZeroCodeHub.service.GenerationRunStateService;
import com.swu.aiZeroCodeHub.service.GenerationAppLockService;
import com.swu.aiZeroCodeHub.service.ArtifactBackupService;
import com.swu.aiZeroCodeHub.config.AiCodeGeneratorServiceFactory;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.swu.aiZeroCodeHub.model.dto.chathistory.ChatHistoryAddRequest;
import com.swu.aiZeroCodeHub.model.enums.ChatHistoryMessageTypeEnum;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用 服务层实现。
 *
 * @author hxyz61
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

    private static final int MAX_USER_PAGE_SIZE = 20;

    private static final int MAX_FEATURED_PAGE_SIZE = 20;

    private static final String FEATURED_APP_CACHE_VER_KEY = "app:featured:ver";

    private static final String FEATURED_APP_CACHE_KEY_PREFIX = "app:featured:page";

    private static final long FEATURED_APP_CACHE_TTL_SECONDS = 60;

    private static final long FEATURED_APP_CACHE_EMPTY_TTL_SECONDS = 10;

    private static final Set<String> USER_ALLOWED_SORT_FIELDS = Set.of("id", "appName", "priority", "createTime", "updateTime", "editTime");

    private static final Set<String> ADMIN_ALLOWED_SORT_FIELDS = Set.of(
            "id", "appName", "cover", "initPrompt", "codeGenType", "deployKey", "deployedTime", "priority", "userId", "createTime", "updateTime", "editTime"
    );

    @Resource
    private UserService userService;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;
    @Resource
    private GenerationDispatcher generationDispatcher;
    @Resource
    private GenerationRunStateService generationRunStateService;
    @Resource
    private GenerationRunProperties generationRunProperties;
    @Resource
    private GenerationAppLockService generationAppLockService;
    @Resource
    private ArtifactBackupService artifactBackupService;
    @Autowired
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Override
    public long createApp(AppCreateRequest appCreateRequest, jakarta.servlet.http.HttpServletRequest request) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(appCreateRequest == null, ErrorCode.PARAM_ERROR);
        User loginUser = getLoginUser(request);

        String initPrompt = appCreateRequest.getInitPrompt();
        if (StrUtil.isBlank(initPrompt)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "initPrompt 不能为空");
        }

        String codeGenType = appCreateRequest.getCodeGenType();
        if (StrUtil.isNotBlank(codeGenType) && CodeGenTypeEnum.getEnumByValue(codeGenType) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "codeGenType 错误");
        }

        App app = new App();
        String appName = appCreateRequest.getAppName();
        if (StrUtil.isBlank(appName)) {
            appName = "未命名应用";
        }
        app.setAppName(appName);
        app.setInitPrompt(initPrompt);
        app.setCover(appCreateRequest.getCover());
        app.setCodeGenType(codeGenType);
        app.setPriority(0);
        app.setUserId(loginUser.getId());
        boolean result = this.save(app);
        ThrowUtils.throwExceptionByConditionAndErrorCode(!result, ErrorCode.OPERATION_ERROR);
        return app.getId();
    }

    @Override
    public boolean updateMyApp(AppUpdateMyRequest appUpdateMyRequest, jakarta.servlet.http.HttpServletRequest request) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(appUpdateMyRequest == null, ErrorCode.PARAM_ERROR);
        User loginUser = getLoginUser(request);

        Long id = appUpdateMyRequest.getId();
        ThrowUtils.throwExceptionByConditionAndErrorCode(id == null || id <= 0, ErrorCode.PARAM_ERROR);
        String appName = appUpdateMyRequest.getAppName();
        if (StrUtil.isBlank(appName)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "应用名称不能为空");
        }

        App oldApp = this.getById(id);
        ThrowUtils.throwExceptionByConditionAndErrorCode(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        if (!loginUser.getId().equals(oldApp.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        App updateApp = new App();
        updateApp.setId(id);
        updateApp.setAppName(appName);
        updateApp.setEditTime(LocalDateTime.now());
        boolean result = this.updateById(updateApp);
        if (result && oldApp.getPriority() != null && oldApp.getPriority() > 0) {
            invalidateFeaturedAppCache();
        }
        return result;
    }

    @Override
    public boolean deleteMyApp(long id, jakarta.servlet.http.HttpServletRequest request) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id <= 0, ErrorCode.PARAM_ERROR);
        User loginUser = getLoginUser(request);

        App oldApp = this.getById(id);
        ThrowUtils.throwExceptionByConditionAndErrorCode(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        if (!loginUser.getId().equals(oldApp.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        
        // 级联删除对话历史
        chatHistoryService.deleteChatHistoryByAppId(id);
        boolean result = this.removeById(id);
        if (result) {
            clearAppChatMemory(id);
        }
        if (result && oldApp.getPriority() != null && oldApp.getPriority() > 0) {
            invalidateFeaturedAppCache();
        }
        return result;
    }

    @Override
    public AppVO getMyAppVoById(long id, jakarta.servlet.http.HttpServletRequest request) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id <= 0, ErrorCode.PARAM_ERROR);
        User loginUser = getLoginUser(request);

        App app = this.getById(id);
        ThrowUtils.throwExceptionByConditionAndErrorCode(app == null, ErrorCode.NOT_FOUND_ERROR);
        
        // Allow access if:
        // 1. User is the creator
        // 2. User is admin
        // 3. App is featured (priority > 0) - Publicly viewable
        boolean isCreator = loginUser.getId().equals(app.getUserId());
        boolean isAdmin = "admin".equals(loginUser.getUserRole());
        boolean isFeatured = app.getPriority() != null && app.getPriority() > 0;
        
        if (!isCreator && !isAdmin && !isFeatured) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return getAppVo(app);
    }

    @Override
    public Page<AppVO> pageMyAppVo(AppMyQueryRequest appMyQueryRequest, jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        QueryWrapper queryWrapper = buildMyAppQueryWrapper(appMyQueryRequest, loginUser.getId());

        long pageNumber = appMyQueryRequest == null ? 1 : appMyQueryRequest.getPageNumber();
        long pageSize = appMyQueryRequest == null ? 10 : appMyQueryRequest.getPageSize();
        if (pageNumber < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageNumber 错误");
        }
        if (pageSize < 1 || pageSize > MAX_USER_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageSize 错误");
        }

        Page<App> appPage = this.mapper.paginate(pageNumber, pageSize, queryWrapper);
        List<AppVO> appVoRecords = getAppVoList(appPage.getRecords());

        Page<AppVO> appVoPage = new Page<>(appPage.getPageNumber(), appPage.getPageSize());
        appVoPage.setTotalRow(appPage.getTotalRow());
        appVoPage.setRecords(appVoRecords);
        return appVoPage;
    }

    @Override
    public Page<AppVO> pageFeaturedAppVo(AppFeaturedQueryRequest appFeaturedQueryRequest) {
        long pageNumber = appFeaturedQueryRequest == null ? 1 : appFeaturedQueryRequest.getPageNumber();
        long pageSize = appFeaturedQueryRequest == null ? 10 : appFeaturedQueryRequest.getPageSize();
        if (pageNumber < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageNumber 错误");
        }
        if (pageSize < 1 || pageSize > MAX_FEATURED_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageSize 错误");
        }

        String cacheKey = buildFeaturedAppCacheKey(pageNumber, pageSize, appFeaturedQueryRequest == null ? null : appFeaturedQueryRequest.getAppName());
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cachedJson)) {
            try {
                return objectMapper.readValue(cachedJson, new TypeReference<Page<AppVO>>() {});
            } catch (Exception e) {
                log.warn("解析精选应用缓存失败，key={}, error={}", cacheKey, e.getMessage());
            }
        }

        QueryWrapper queryWrapper = buildFeaturedAppQueryWrapper(appFeaturedQueryRequest);
        Page<App> appPage = this.mapper.paginate(pageNumber, pageSize, queryWrapper);
        List<AppVO> appVoRecords = getAppVoList(appPage.getRecords());

        Page<AppVO> appVoPage = new Page<>(appPage.getPageNumber(), appPage.getPageSize());
        appVoPage.setTotalRow(appPage.getTotalRow());
        appVoPage.setRecords(appVoRecords);
        try {
            long ttl = CollUtil.isEmpty(appVoRecords) ? FEATURED_APP_CACHE_EMPTY_TTL_SECONDS : FEATURED_APP_CACHE_TTL_SECONDS;
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(appVoPage), Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("写入精选应用缓存失败，key={}, error={}", cacheKey, e.getMessage());
        }
        return appVoPage;
    }

    @Override
    public boolean adminDeleteApp(long id) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id <= 0, ErrorCode.PARAM_ERROR);
        App oldApp = this.getById(id);
        ThrowUtils.throwExceptionByConditionAndErrorCode(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        
        // 级联删除对话历史
        chatHistoryService.deleteChatHistoryByAppId(id);
        boolean result = this.removeById(id);
        if (result) {
            clearAppChatMemory(id);
        }
        if (result && oldApp.getPriority() != null && oldApp.getPriority() > 0) {
            invalidateFeaturedAppCache();
        }
        return result;
    }

    @Override
    public boolean adminUpdateApp(AppAdminUpdateRequest appAdminUpdateRequest) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(appAdminUpdateRequest == null, ErrorCode.PARAM_ERROR);
        Long id = appAdminUpdateRequest.getId();
        ThrowUtils.throwExceptionByConditionAndErrorCode(id == null || id <= 0, ErrorCode.PARAM_ERROR);

        App oldApp = this.getById(id);
        ThrowUtils.throwExceptionByConditionAndErrorCode(oldApp == null, ErrorCode.NOT_FOUND_ERROR);

        App updateApp = new App();
        updateApp.setId(id);
        if (StrUtil.isNotBlank(appAdminUpdateRequest.getAppName())) {
            updateApp.setAppName(appAdminUpdateRequest.getAppName());
        }
        if (StrUtil.isNotBlank(appAdminUpdateRequest.getCover())) {
            updateApp.setCover(appAdminUpdateRequest.getCover());
        }
        if (appAdminUpdateRequest.getPriority() != null) {
            updateApp.setPriority(appAdminUpdateRequest.getPriority());
        }
        updateApp.setEditTime(LocalDateTime.now());
        boolean result = this.updateById(updateApp);
        if (result) {
            boolean oldFeatured = oldApp.getPriority() != null && oldApp.getPriority() > 0;
            boolean newFeatured = appAdminUpdateRequest.getPriority() != null && appAdminUpdateRequest.getPriority() > 0;
            boolean updateName = StrUtil.isNotBlank(appAdminUpdateRequest.getAppName());
            if (oldFeatured || newFeatured || updateName) {
                invalidateFeaturedAppCache();
            }
        }
        return result;
    }

    @Override
    public AppVO adminGetAppVoById(long id) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id <= 0, ErrorCode.PARAM_ERROR);
        App app = this.getById(id);
        ThrowUtils.throwExceptionByConditionAndErrorCode(app == null, ErrorCode.NOT_FOUND_ERROR);
        return getAppVo(app);
    }

    @Override
    public Page<AppVO> adminPageAppVo(AppAdminQueryRequest appAdminQueryRequest) {
        QueryWrapper queryWrapper = buildAdminAppQueryWrapper(appAdminQueryRequest);

        long pageNumber = appAdminQueryRequest == null ? 1 : appAdminQueryRequest.getPageNumber();
        long pageSize = appAdminQueryRequest == null ? 10 : appAdminQueryRequest.getPageSize();
        if (pageNumber < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageNumber 错误");
        }
        if (pageSize < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageSize 错误");
        }

        Page<App> appPage = this.mapper.paginate(pageNumber, pageSize, queryWrapper);
        List<AppVO> appVoRecords = getAppVoList(appPage.getRecords());

        Page<AppVO> appVoPage = new Page<>(appPage.getPageNumber(), appPage.getPageSize());
        appVoPage.setTotalRow(appPage.getTotalRow());
        appVoPage.setRecords(appVoRecords);
        return appVoPage;
    }

    private User getLoginUser(jakarta.servlet.http.HttpServletRequest request) {
        Object objectUser = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (objectUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User user = (User) objectUser;
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User currentUser = userService.getById(user.getId());
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    private AppVO getAppVo(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVo = new AppVO();
        BeanUtils.copyProperties(app, appVo);
        return appVo;
    }

    private List<AppVO> getAppVoList(List<App> apps) {
        if (CollUtil.isEmpty(apps)) {
            return new ArrayList<>();
        }
        List<AppVO> appVoList = new ArrayList<>(apps.size());
        for (App app : apps) {
            appVoList.add(getAppVo(app));
        }
        return appVoList;
    }

    private QueryWrapper buildMyAppQueryWrapper(AppMyQueryRequest appMyQueryRequest, long userId) {
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userId", userId);
        if (appMyQueryRequest == null) {
            queryWrapper.orderBy("id desc");
            return queryWrapper;
        }
        if (StrUtil.isNotBlank(appMyQueryRequest.getAppName())) {
            queryWrapper.like("appName", appMyQueryRequest.getAppName());
        }
        applySort(queryWrapper, appMyQueryRequest.getSortField(), appMyQueryRequest.getSortOrder(), USER_ALLOWED_SORT_FIELDS);
        return queryWrapper;
    }

    private QueryWrapper buildFeaturedAppQueryWrapper(AppFeaturedQueryRequest appFeaturedQueryRequest) {
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.gt("priority", 0);
        if (appFeaturedQueryRequest != null && StrUtil.isNotBlank(appFeaturedQueryRequest.getAppName())) {
            queryWrapper.like("appName", appFeaturedQueryRequest.getAppName());
        }
        queryWrapper.orderBy("priority desc");
        queryWrapper.orderBy("id desc");
        return queryWrapper;
    }

    private QueryWrapper buildAdminAppQueryWrapper(AppAdminQueryRequest appAdminQueryRequest) {
        QueryWrapper queryWrapper = new QueryWrapper();
        if (appAdminQueryRequest == null) {
            queryWrapper.orderBy("id desc");
            return queryWrapper;
        }
        Long id = appAdminQueryRequest.getId();
        if (id != null) {
            if (id <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "id 错误");
            }
            queryWrapper.eq("id", id);
        }
        if (StrUtil.isNotBlank(appAdminQueryRequest.getAppName())) {
            queryWrapper.like("appName", appAdminQueryRequest.getAppName());
        }
        if (StrUtil.isNotBlank(appAdminQueryRequest.getCover())) {
            queryWrapper.eq("cover", appAdminQueryRequest.getCover());
        }
        if (StrUtil.isNotBlank(appAdminQueryRequest.getInitPrompt())) {
            queryWrapper.like("initPrompt", appAdminQueryRequest.getInitPrompt());
        }
        if (StrUtil.isNotBlank(appAdminQueryRequest.getCodeGenType())) {
            queryWrapper.eq("codeGenType", appAdminQueryRequest.getCodeGenType());
        }
        if (StrUtil.isNotBlank(appAdminQueryRequest.getDeployKey())) {
            queryWrapper.eq("deployKey", appAdminQueryRequest.getDeployKey());
        }
        if (appAdminQueryRequest.getPriority() != null) {
            queryWrapper.eq("priority", appAdminQueryRequest.getPriority());
        }
        if (appAdminQueryRequest.getUserId() != null) {
            Long userId = appAdminQueryRequest.getUserId();
            if (userId <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "userId 错误");
            }
            queryWrapper.eq("userId", userId);
        }
        applySort(queryWrapper, appAdminQueryRequest.getSortField(), appAdminQueryRequest.getSortOrder(), ADMIN_ALLOWED_SORT_FIELDS);
        return queryWrapper;
    }

    private void applySort(QueryWrapper queryWrapper, String sortField, String sortOrder, Set<String> allowedSortFields) {
        if (StrUtil.isNotBlank(sortField)) {
            if (!allowedSortFields.contains(sortField)) {
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
    }

    private String buildFeaturedAppCacheKey(long pageNumber, long pageSize, String appName) {
        String ver = stringRedisTemplate.opsForValue().get(FEATURED_APP_CACHE_VER_KEY);
        if (StrUtil.isBlank(ver)) {
            ver = "0";
        }
        String namePart = StrUtil.isBlank(appName) ? "_" : DigestUtil.sha256Hex(appName.trim());
        return FEATURED_APP_CACHE_KEY_PREFIX + ":v" + ver + ":" + pageNumber + ":" + pageSize + ":" + namePart;
    }

    private void invalidateFeaturedAppCache() {
        try {
            stringRedisTemplate.opsForValue().increment(FEATURED_APP_CACHE_VER_KEY);
        } catch (Exception e) {
            log.warn("失效精选应用缓存失败: {}", e.getMessage());
        }
    }

    private void clearAppChatMemory(long appId) {
        try {
            // RedisChatMemoryStore.deleteMessages 使用精确 memoryId 删除，不影响其他应用。
            redisChatMemoryStore.deleteMessages(appId);
        } catch (Exception e) {
            log.warn("清理应用 Redis 对话记忆失败，appId={}: {}", appId, e.getMessage());
        }
        aiCodeGeneratorServiceFactory.invalidateAppCache(appId);
    }

    /**
     * 调用AI核心业务生产代码
     * @param appId
     * @param message
     * @param loginUser
     * @return
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String message, String codeGenType, User loginUser) {
        return chatToGenCode(appId, message, codeGenType, null, loginUser)
                .filter(event -> "message".equals(event.type()))
                .map(event -> event.data() == null ? "" : String.valueOf(event.data()));
    }

    @Override
    public Flux<GenerationEvent> chatToGenCode(Long appId, String message, String codeGenType,
                                               String executionMode, User loginUser) {
        GenerationCreateRequest createRequest = new GenerationCreateRequest();
        createRequest.setAppId(appId);
        createRequest.setUserMessage(message);
        createRequest.setCodeGenType(codeGenType);
        createRequest.setExecutionMode(executionMode);
        return prepareGeneration(createRequest, loginUser).events();
    }

    @Override
    public GenerationCreateVO createGeneration(GenerationCreateRequest request, User loginUser) {
        PreparedGeneration prepared = prepareGeneration(request, loginUser);
        return new GenerationCreateVO(prepared.request().runId(), "PENDING");
    }

    @Override
    public Flux<GenerationEvent> subscribeGeneration(String runId, User loginUser) {
        return subscribeGeneration(runId, loginUser, 0L);
    }

    @Override
    public Flux<GenerationEvent> subscribeGeneration(String runId, User loginUser, long afterSequence) {
        GenerationRunState state = getAuthorizedRunState(runId, loginUser);
        return generationDispatcher.subscribe(state.runId(), Math.max(0L, afterSequence));
    }

    @Override
    public GenerationRunState getGenerationStatus(String runId, User loginUser) {
        return getAuthorizedRunState(runId, loginUser);
    }

    private PreparedGeneration prepareGeneration(GenerationCreateRequest request, User loginUser) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(request == null, ErrorCode.PARAM_ERROR);
        Long appId = request.getAppId();
        String message = request.getUserMessage();
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId == null || appId <= 0,
                ErrorCode.PARAM_ERROR, "应用ID不能为空");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(StrUtil.isBlank(message),
                ErrorCode.PARAM_ERROR, "用户提示词不能为空");
        ChatHistoryService.validateUserMessageLength(message);
        ThrowUtils.throwExceptionByConditionAndErrorCode(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        App app = this.getById(appId);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (app.getUserId() == null || !app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }

        ExecutionModeEnum mode = ExecutionModeEnum.parse(request.getExecutionMode());
        String type = StrUtil.isNotBlank(request.getCodeGenType()) ? request.getCodeGenType() : app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(type);
        if (StrUtil.isNotBlank(type) && codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "codeGenType 错误");
        }
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "codeGenType 必须明确指定，请选择 HTML、多文件或 Vue 项目模式");
        }
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT
                && (vueProjectBuilder == null || !vueProjectBuilder.isBuildCapabilityAvailable())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "Vue 构建能力未配置，请配置远程隔离构建服务或切换到 HTML/多文件模式");
        }

        if (StrUtil.isNotBlank(request.getCodeGenType()) && !request.getCodeGenType().equals(app.getCodeGenType())) {
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCodeGenType(request.getCodeGenType());
            updateApp.setEditTime(LocalDateTime.now());
            this.updateById(updateApp);
        }

        GenerationRequest generationRequest = new GenerationRequest(
                appId, loginUser.getId(), message, codeGenTypeEnum, mode, loginUser, null);
        boolean lockAcquired = generationAppLockService == null
                || generationAppLockService.acquire(appId, generationRequest.runId());
        if (!lockAcquired) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该应用已有生成任务正在运行");
        }

        boolean stateCreated = false;
        boolean generationStarted = false;
        try {
            if (artifactBackupService != null) {
                artifactBackupService.prepare(appId, generationRequest.runId());
            }
            // 新任务必须先成功写入用户历史，再创建 Redis 状态并启动 Agent。
            saveChatHistoryStrict(appId, message, ChatHistoryMessageTypeEnum.USER, loginUser);
            int maxRetry = generationRunProperties == null ? 2 : generationRunProperties.effectiveMaxRetryCount();
            int maxRepair = generationRunProperties == null ? 1 : generationRunProperties.effectiveMaxRepairAttempts();
            // 保留两参数调用的兼容性；其默认的 maxRepairAttempts 已固定为 1。
            if (maxRepair == 1) {
                generationRunStateService.create(generationRequest, maxRetry);
            } else {
                generationRunStateService.create(generationRequest, maxRetry, maxRepair);
            }
            stateCreated = true;
            Flux<GenerationEvent> events = generationDispatcher.generate(generationRequest);
            generationStarted = true;
            return new PreparedGeneration(generationRequest, events);
        } catch (Throwable error) {
            if (stateCreated) {
                try {
                    generationRunStateService.transitionToRunning(generationRequest.runId(), "启动失败");
                    generationRunStateService.finish(generationRequest.runId(),
                            com.swu.aiZeroCodeHub.generation.GenerationRunStatus.FAILED, "生成任务启动失败");
                } catch (Exception ignored) {
                    log.warn("生成任务启动失败且状态无法收敛，runId={}, appId={}, mode={}, step={}",
                            generationRequest.runId(), appId, mode, "启动");
                }
            }
            throw error;
        } finally {
            if (generationAppLockService != null && (!stateCreated || !generationStarted)) {
                generationAppLockService.release(appId, generationRequest.runId());
            }
        }
    }

    private GenerationRunState getAuthorizedRunState(String runId, User loginUser) {
        GenerationRunState state = generationRunStateService.getRequired(runId);
        boolean admin = loginUser != null && UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean owner = loginUser != null && loginUser.getId() != null
                && loginUser.getId().equals(state.userId());
        if (!admin && !owner) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该生成任务");
        }
        return state;
    }

    @Override
    public boolean cancelGeneration(String runId, User loginUser) {
        // Redis 状态中的 appId 与 userId 是取消权限的依据；应用仍存在且归属未改变时才允许普通用户继续。
        generationRunStateService.find(runId).ifPresent(state -> {
            App app = this.getById(state.appId());
            boolean admin = loginUser != null && UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
            boolean owner = loginUser != null && loginUser.getId() != null
                    && loginUser.getId().equals(state.userId())
                    && app != null && loginUser.getId().equals(app.getUserId());
            if (!admin && !owner) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限取消该生成任务");
            }
        });
        GenerationRunStateService.CancelResult result = generationRunStateService.requestCancel(runId, loginUser);
        return switch (result) {
            case REQUESTED -> true;
            case NOT_FOUND -> throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "生成任务不存在");
            case FORBIDDEN -> throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限取消该生成任务");
            case NOT_ACTIVE -> throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成任务已结束，无法取消");
        };
    }


    /**
     * 保存对话历史
     *
     * @param appId
     * @param content
     * @param messageTypeEnum
     * @param loginUser
     */
    private void saveChatHistory(Long appId, String content, ChatHistoryMessageTypeEnum messageTypeEnum, User loginUser) {
        try {
            ChatHistoryAddRequest addRequest = new ChatHistoryAddRequest();
            addRequest.setAppId(appId);
            addRequest.setContent(content);
            addRequest.setMessageType(messageTypeEnum.getValue());
            chatHistoryService.addChatHistory(addRequest, loginUser);
        } catch (Exception e) {
            log.error("保存对话历史失败: appId={}, type={}, error={}", appId, messageTypeEnum.getText(), e.getMessage());
        }
    }

    private void saveChatHistoryStrict(Long appId, String content,
                                       ChatHistoryMessageTypeEnum messageTypeEnum, User loginUser) {
        ChatHistoryAddRequest addRequest = new ChatHistoryAddRequest();
        addRequest.setAppId(appId);
        addRequest.setContent(content);
        addRequest.setMessageType(messageTypeEnum.getValue());
        chatHistoryService.addChatHistory(addRequest, loginUser);
    }

    private record PreparedGeneration(GenerationRequest request, Flux<GenerationEvent> events) {
    }




    /**
     * 部署AI生成的网页代码
     * @param appId
     * @param loginUser
     * @return
     */
    @Override
    public String deployApp(Long appId,User loginUser){
        //参数校验
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId==null||appId<=0,ErrorCode.PARAM_ERROR,"应用ID不能为空");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(loginUser==null,ErrorCode.NOT_LOGIN_ERROR,"用户未登陆");
        //查询应用
        App app=this.getById(appId);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(app==null,ErrorCode.NOT_FOUND_ERROR,"应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"无权限部署应用");
        }

        CodeOutputResolveResult resolveResult = resolveCodeOutputDir(appId, app.getCodeGenType());
        if (resolveResult == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,"应用代码不存在，请先生成代码");
        }
        String codeGenType = resolveResult.codeGenType;
        File sourceDir = resolveResult.dir;

        // 统一 code_deploy 与 code_output 的目录命名规则：{type}_{appId}
        // 这样部署后的 URL 路径也更具语义，且多次部署会覆盖同一目录（符合预览/更新逻辑）
        String deployKey = codeGenType + "_" + appId;
        String sourceDirPath = sourceDir.getAbsolutePath();

        // 如果部署的是vue项目，使用单独部署器部署
        // 7. Vue项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            if (vueProjectBuilder == null || !vueProjectBuilder.isBuildCapabilityAvailable()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "Vue 构建能力未配置，请配置远程隔离构建服务后再部署");
            }
            // 工作流已完成一次构建并产出 dist 时直接复用，避免部署再次执行 npm。
            File existingDist = new File(sourceDirPath, "dist");
            if (!existingDist.isDirectory()) {
                boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
                ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(!buildSuccess,
                        ErrorCode.SYSTEM_ERROR, "Vue项目构建失败，请检查代码和依赖");
            }

            // 检查dist目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue项目构建完成但未生成dist目录");

            // 将dist目录作为部署源
            sourceDir = distDir;
            log.info("Vue项目构建成功，将部署dist目录: {}", distDir.getAbsolutePath());
        }


        //复制文件到部署目录
        String deployDirPath=AppConstant.CODE_DEPLOY_ROOT_DIR+File.separator+deployKey;
        try{
            FileUtil.copyContent(sourceDir,new File(deployDirPath),true);

        }catch (Exception e){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"部署失败:"+e.getMessage());
        }

        //更新应用的deployKey和部署时间
        App updatedApp=new App();
        updatedApp.setId(appId);
        updatedApp.setDeployKey(deployKey);
        updatedApp.setDeployedTime(LocalDateTime.now());
        if (!codeGenType.equals(app.getCodeGenType())) {
            updatedApp.setCodeGenType(codeGenType);
        }
        boolean updateResult = this.updateById(updatedApp);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(!updateResult,ErrorCode.OPERATION_ERROR,"更新应用部署信息失败");
        //返回可访问URL
        return String.format("/api/app/%s/index.html",deployKey);

    }

    private CodeOutputResolveResult resolveCodeOutputDir(Long appId, String preferredType) {
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
                return new CodeOutputResolveResult(type, sourceDir);
            }
        }
        return null;
    }

    private static class CodeOutputResolveResult {
        private final String codeGenType;
        private final File dir;

        private CodeOutputResolveResult(String codeGenType, File dir) {
            this.codeGenType = codeGenType;
            this.dir = dir;
        }
    }




}
