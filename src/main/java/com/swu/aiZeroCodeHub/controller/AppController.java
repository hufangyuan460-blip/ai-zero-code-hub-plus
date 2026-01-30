package com.swu.aiZeroCodeHub.controller;

import com.mybatisflex.core.paginate.Page;
import com.swu.aiZeroCodeHub.annotation.AuthCheck;
import com.swu.aiZeroCodeHub.common.ResultUtils;
import com.swu.aiZeroCodeHub.common.vo.BaseResponse;
import com.swu.aiZeroCodeHub.constant.UserConstant;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.model.dto.app.AppAdminQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppAdminUpdateRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppCreateRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppFeaturedQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppMyQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppUpdateMyRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.swu.aiZeroCodeHub.service.AppService;
import com.swu.aiZeroCodeHub.model.vo.app.AppVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
