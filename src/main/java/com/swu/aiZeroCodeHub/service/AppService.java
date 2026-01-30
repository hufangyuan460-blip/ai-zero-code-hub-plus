package com.swu.aiZeroCodeHub.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.swu.aiZeroCodeHub.model.dto.app.AppAdminQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppAdminUpdateRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppCreateRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppFeaturedQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppMyQueryRequest;
import com.swu.aiZeroCodeHub.model.dto.app.AppUpdateMyRequest;
import com.swu.aiZeroCodeHub.model.entity.App;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.vo.app.AppVO;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Flux;

/**
 * 应用 服务层。
 *
 * @author hxyz61
 */
public interface AppService extends IService<App> {

    long createApp(AppCreateRequest appCreateRequest, HttpServletRequest request);

    boolean updateMyApp(AppUpdateMyRequest appUpdateMyRequest, HttpServletRequest request);

    boolean deleteMyApp(long id, HttpServletRequest request);

    AppVO getMyAppVoById(long id, HttpServletRequest request);

    Page<AppVO> pageMyAppVo(AppMyQueryRequest appMyQueryRequest, HttpServletRequest request);

    Page<AppVO> pageFeaturedAppVo(AppFeaturedQueryRequest appFeaturedQueryRequest);

    boolean adminDeleteApp(long id);

    boolean adminUpdateApp(AppAdminUpdateRequest appAdminUpdateRequest);

    AppVO adminGetAppVoById(long id);

    Page<AppVO> adminPageAppVo(AppAdminQueryRequest appAdminQueryRequest);

    /**
     * 调用AI核心业务生成代码
     * @param appId
     * @param message
     * @param loginUser
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);


}
