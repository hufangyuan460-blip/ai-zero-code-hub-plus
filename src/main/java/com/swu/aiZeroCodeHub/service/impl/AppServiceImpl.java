package com.swu.aiZeroCodeHub.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.swu.aiZeroCodeHub.constant.AppConstant;
import com.swu.aiZeroCodeHub.constant.UserConstant;
import com.swu.aiZeroCodeHub.core.AiCodeGeneratorFacade;
import com.swu.aiZeroCodeHub.core.builder.VueProjectBuilder;
import com.swu.aiZeroCodeHub.core.executor.StreamHandlerExecutor;
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
import com.swu.aiZeroCodeHub.mapper.AppMapper;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.app.AppVO;
import com.swu.aiZeroCodeHub.service.AppService;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import com.swu.aiZeroCodeHub.service.ScreenshotService;
import com.swu.aiZeroCodeHub.service.UserService;
import com.swu.aiZeroCodeHub.aiTool.FileWriteTool;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.LocalDateTime;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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

    private static final Set<String> USER_ALLOWED_SORT_FIELDS = Set.of("id", "appName", "priority", "createTime", "updateTime", "editTime");

    private static final Set<String> ADMIN_ALLOWED_SORT_FIELDS = Set.of(
            "id", "appName", "cover", "initPrompt", "codeGenType", "deployKey", "deployedTime", "priority", "userId", "createTime", "updateTime", "editTime"
    );

    @Resource
    private UserService userService;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Autowired
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Autowired
    private StreamHandlerExecutor streamHandlerExecutor;
    @Autowired
    private VueProjectBuilder vueProjectBuilder;
    @Autowired
    private ScreenshotService screenshotService;
    @Autowired
    private FileWriteTool fileWriteTool;

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
        return this.updateById(updateApp);
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
        
        return this.removeById(id);
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
        QueryWrapper queryWrapper = buildFeaturedAppQueryWrapper(appFeaturedQueryRequest);

        long pageNumber = appFeaturedQueryRequest == null ? 1 : appFeaturedQueryRequest.getPageNumber();
        long pageSize = appFeaturedQueryRequest == null ? 10 : appFeaturedQueryRequest.getPageSize();
        if (pageNumber < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "pageNumber 错误");
        }
        if (pageSize < 1 || pageSize > MAX_FEATURED_PAGE_SIZE) {
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
    public boolean adminDeleteApp(long id) {
        ThrowUtils.throwExceptionByConditionAndErrorCode(id <= 0, ErrorCode.PARAM_ERROR);
        App oldApp = this.getById(id);
        ThrowUtils.throwExceptionByConditionAndErrorCode(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        
        // 级联删除对话历史
        chatHistoryService.deleteChatHistoryByAppId(id);
        
        return this.removeById(id);
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
        return this.updateById(updateApp);
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
        String cover = appVo.getCover();
        String localCover = String.format("/api/static/covers/%d.jpg", appVo.getId());
        String localCoverPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "covers" + File.separator + appVo.getId() + ".jpg";
        if (cover == null || cover.isBlank() || cover.contains("/screenshots/")) {
            File localFile = new File(localCoverPath);
            if (localFile.exists() && localFile.isFile() && localFile.length() > 0) {
                appVo.setCover(localCover);
            }
        }
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

    /**
     * 调用AI核心业务生产代码
     * @param appId
     * @param message
     * @param loginUser
     * @return
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String message, String codeGenType, User loginUser) {
        //参数校验
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用ID不能为空");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(StrUtil.isBlank(message), ErrorCode.PARAM_ERROR, "用户提示词不能为空");

        //查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }

        //获取应用代码生成类型：优先使用参数传入的类型，如果为空则使用应用配置的类型
        String type = StrUtil.isNotBlank(codeGenType) ? codeGenType : app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(type);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }

        // 1. 保存用户消息
        saveChatHistory(appId, message, ChatHistoryMessageTypeEnum.USER, loginUser);

        // 2. 调用AI生成，并捕获响应流
        Flux<String> fluxResponse = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);

        Flux<String> handledFlux = streamHandlerExecutor.doExecute(fluxResponse,chatHistoryService,appId,loginUser,codeGenTypeEnum);
        return handledFlux.doOnComplete(() -> {
            Thread.startVirtualThread(() -> {
                try {
                    App latestApp = this.getById(appId);
                    if (latestApp == null) {
                        return;
                    }
                    String sourceDirName = codeGenTypeEnum.getValue() + "_" + appId;
                    String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
                    File sourceDir = new File(sourceDirPath);
                    boolean dirReady = waitForDirectoryReady(sourceDir, 60, 1000L);
                    if (!dirReady) {
                        log.error("应用代码不存在，无法自动部署：{}", sourceDirPath);
                        return;
                    }
                    if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
                        boolean vueReady = waitForVueProjectReady(appId, sourceDirPath, 120, 1000L);
                        if (!vueReady) {
                            log.error("Vue 项目未就绪，无法自动部署：{}", sourceDirPath);
                            return;
                        }
                        deployVueProject(appId, sourceDirName, sourceDirPath, latestApp);
                    } else {
                        File indexFile = new File(sourceDir, "index.html");
                        boolean ready = waitForFileReady(indexFile, 20, 1000L);
                        if (!ready) {
                            log.error("index.html 未就绪，无法自动部署：{}", indexFile.getAbsolutePath());
                            return;
                        }
                        deployStandardProject(appId, sourceDirName, sourceDir, latestApp);
                    }
                } catch (Exception e) {
                    log.error("自动部署并生成封面失败，appId: {}", appId, e);
                }
            });
        });
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




    /**
     * 部署AI生成的网页代码
     */
    @Override
    public String deployApp(Long appId, User loginUser) {
        // 参数校验（保持不变）
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用ID不能为空");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登陆");

        // 查询应用
        App app = this.getById(appId);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署应用");
        }

        // 获取源代码目录
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        String sourceDirName = codeGenTypeEnum.getValue() + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        String deployKey = sourceDirName;

        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "应用代码不存在，请先生成代码");
        }

        // Vue项目特殊处理
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            return deployVueProject(appId, deployKey, sourceDirPath, app);
        } else {
            return deployStandardProject(appId, deployKey, sourceDir, app);
        }
    }

    /**
     * 部署Vue项目
     */
    private String deployVueProject(Long appId, String deployKey, String sourceDirPath, App app) {
        try {
            log.info("开始部署Vue项目，appId: {}, sourceDir: {}", appId, sourceDirPath);

            // 1. 构建Vue项目
            boolean ready = waitForVueProjectReady(appId, sourceDirPath, 90, 1000L);
            if (!ready) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue项目生成未完成，无法部署");
            }
            log.info("执行Vue项目构建...");
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            if (!buildSuccess) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue项目构建失败");
            }

            // 2. 检查构建输出目录
            File distDir = new File(sourceDirPath, "dist");
            File htmlDir = new File(sourceDirPath, "html"); // 有些配置可能输出到html目录

            File buildOutputDir = null;
            if (distDir.exists() && distDir.isDirectory()) {
                buildOutputDir = distDir;
                log.info("检测到dist目录: {}", distDir.getAbsolutePath());
            } else if (htmlDir.exists() && htmlDir.isDirectory()) {
                buildOutputDir = htmlDir;
                log.info("检测到html目录: {}", htmlDir.getAbsolutePath());
            } else {
                // 检查其他可能的输出目录
                File[] possibleDirs = new File(sourceDirPath).listFiles((dir, name) ->
                        name.equals("dist") || name.equals("html") || name.equals("build") || name.equals("output"));

                if (possibleDirs != null && possibleDirs.length > 0) {
                    for (File dir : possibleDirs) {
                        if (dir.isDirectory()) {
                            buildOutputDir = dir;
                            log.info("检测到构建输出目录: {}", dir.getAbsolutePath());
                            break;
                        }
                    }
                }
            }

            if (buildOutputDir == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "Vue项目构建成功但未找到输出目录，请检查构建配置");
            }

            // 3. 准备部署目录
            String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
            File deployDir = new File(deployDirPath);

            // 清理已存在的部署目录
            if (deployDir.exists()) {
                FileUtil.del(deployDir);
            }
            FileUtil.mkdir(deployDir);

            // 4. 复制构建结果到部署目录
            log.info("复制构建文件从 {} 到 {}", buildOutputDir.getAbsolutePath(), deployDirPath);
            FileUtil.copyContent(buildOutputDir, deployDir, true);

            // 5. 验证部署文件
            File indexFile = new File(deployDir, "index.html");
            if (!indexFile.exists()) {
                // 检查其他可能的入口文件
                String[] possibleIndexFiles = {"index.html", "Index.html", "INDEX.HTML"};
                boolean foundIndex = false;
                for (String fileName : possibleIndexFiles) {
                    if (new File(deployDir, fileName).exists()) {
                        foundIndex = true;
                        break;
                    }
                }
                if (!foundIndex) {
                    log.warn("部署目录中未找到index.html文件，部署内容: {}",
                            Arrays.toString(deployDir.list()));
                }
            }

            // 6. 更新应用部署信息
            updateAppDeployInfo(appId, deployKey);

            // 部署地址
            String appDeployUrl = String.format("%s/%s/index.html", AppConstant.CODE_DEPLOY_HOST, deployKey);
            //异步截图生成封面
            generateAppScreenshotAsync(appId, appDeployUrl);
            log.info("Vue项目部署成功，访问地址: {}", appDeployUrl);
            return appDeployUrl;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Vue项目部署失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue项目部署失败: " + e.getMessage());
        }
    }

    /**
     * 部署标准项目（非Vue）
     */
    private String deployStandardProject(Long appId, String deployKey, File sourceDir, App app) {
        try {
            log.info("开始部署标准项目，appId: {}, sourceDir: {}", appId, sourceDir.getAbsolutePath());

            String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
            File deployDir = new File(deployDirPath);

            // 清理已存在的部署目录
            if (deployDir.exists()) {
                FileUtil.del(deployDir);
            }
            FileUtil.mkdir(deployDir);

            // 复制文件到部署目录
            FileUtil.copyContent(sourceDir, deployDir, true);

            // 更新应用部署信息
            updateAppDeployInfo(appId, deployKey);

            // 部署地址
            String appDeployUrl = String.format("%s/%s/index.html", AppConstant.CODE_DEPLOY_HOST, deployKey);
            //异步截图生成封面
            generateAppScreenshotAsync(appId, appDeployUrl);


            return appDeployUrl;

        } catch (Exception e) {
            log.error("标准项目部署失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目部署失败: " + e.getMessage());
        }
    }

    /**
     * 异步截图
     * @param appId
     * @param appUrl
     */
    private void generateAppScreenshotAsync(Long appId, String appUrl) {
        generateAppScreenshotAsync(appId, appUrl, 2);
    }

    private void generateAppScreenshotAsync(Long appId, String appUrl, int retries) {
        Thread.startVirtualThread(() -> {
            try {
                boolean ready = waitForUrlReady(appUrl, 12, 3000L);
                if (!ready) {
                    if (retries > 0) {
                        try {
                            Thread.sleep(30000L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        generateAppScreenshotAsync(appId, appUrl, retries - 1);
                    } else {
                        log.error("封面截图页面未就绪，appId: {}，appUrl: {}", appId, appUrl);
                    }
                    return;
                }
                String screenshotUrl = screenshotService.generateAndUploadScreenshot(appId, appUrl);
                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCover(screenshotUrl);
                boolean updated = this.updateById(updateApp);
                if (!updated) {
                    log.error("更新应用封面字段失败，appId: {}", appId);
                } else {
                    log.info("应用封面已更新，appId: {}，截图URL: {}", appId, screenshotUrl);
                }
            } catch (Exception e) {
                log.error("异步生成应用截图失败，appId: {}，appUrl: {}", appId, appUrl, e);
            }
        });
    }

    private boolean waitForFileReady(File file, int attempts, long sleepMs) {
        for (int i = 0; i < attempts; i++) {
            if (file.exists() && file.isFile() && file.length() > 0) {
                return true;
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean waitForDirectoryReady(File dir, int attempts, long sleepMs) {
        for (int i = 0; i < attempts; i++) {
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null && files.length > 0) {
                    return true;
                }
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean waitForUrlReady(String url, int attempts, long sleepMs) {
        for (int i = 0; i < attempts; i++) {
            int code = getStatusCode(url);
            if (code >= 200 && code < 300) {
                return true;
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private int getStatusCode(String urlStr) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            return connection.getResponseCode();
        } catch (Exception e) {
            return -1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean waitForVueProjectReady(Long appId, String sourceDirPath, int attempts, long sleepMs) {
        File projectDir = new File(sourceDirPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return false;
        }
        File packageJson = new File(projectDir, "package.json");
        File viteConfigJs = new File(projectDir, "vite.config.js");
        File viteConfigTs = new File(projectDir, "vite.config.ts");
        File srcDir = new File(projectDir, "src");
        File mainJs = new File(srcDir, "main.js");
        File mainTs = new File(srcDir, "main.ts");
        File appVue = new File(srcDir, "App.vue");
        for (int i = 0; i < attempts; i++) {
            boolean filesReady = packageJson.exists()
                    && (viteConfigJs.exists() || viteConfigTs.exists())
                    && srcDir.exists()
                    && (mainJs.exists() || mainTs.exists())
                    && appVue.exists();
            boolean statusReady = true;
            if (fileWriteTool.hasGenerationStatus(appId)) {
                statusReady = fileWriteTool.isGenerationCompleted(appId) && fileWriteTool.isWriteIdle(appId, 3000L);
            }
            if (filesReady && statusReady) {
                return true;
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 更新应用部署信息
     */
    private void updateAppDeployInfo(Long appId, String deployKey) {
        App updatedApp = new App();
        updatedApp.setId(appId);
        updatedApp.setDeployKey(deployKey);
        updatedApp.setDeployedTime(LocalDateTime.now());

        boolean updateResult = this.updateById(updatedApp);
        if (!updateResult) {
            log.error("更新应用部署信息失败，appId: {}", appId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        }
    }




}
