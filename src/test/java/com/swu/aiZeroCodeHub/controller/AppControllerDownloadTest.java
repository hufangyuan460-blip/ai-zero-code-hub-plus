package com.swu.aiZeroCodeHub.controller;

import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.model.entity.App;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.service.AppService;
import com.swu.aiZeroCodeHub.service.ProjectDownloadService;
import com.swu.aiZeroCodeHub.service.ScreenshotService;
import com.swu.aiZeroCodeHub.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppControllerDownloadTest {

    private static final long APP_ID = 1001L;
    private static final long USER_ID = 2002L;
    private static final String NOT_DEPLOYED_MESSAGE = "应用尚未成功部署，部署完成后才能下载源码";

    @Mock
    private AppService appService;
    @Mock
    private UserService userService;
    @Mock
    private ProjectDownloadService projectDownloadService;

    private AppController appController;
    private HttpServletRequest request;
    private User loginUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        appController = new AppController();
        ReflectionTestUtils.setField(appController, "appService", appService);
        ReflectionTestUtils.setField(appController, "userService", userService);
        ReflectionTestUtils.setField(appController, "projectDownloadService", projectDownloadService);
        ReflectionTestUtils.setField(appController, "screenshotService", mock(ScreenshotService.class));

        request = mock(HttpServletRequest.class);
        loginUser = new User();
        loginUser.setId(USER_ID);
        when(userService.getLoginUser(request)).thenReturn(loginUser);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void getDownloadLinkRejectsMissingDeployKey(String deployKey) {
        App app = deployedFieldsApp();
        app.setDeployKey(deployKey);
        when(appService.getById(APP_ID)).thenReturn(app);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> appController.getDownloadLink(APP_ID, request));

        assertDownloadBlocked(exception);
        verify(projectDownloadService, never()).createDownloadToken(APP_ID, USER_ID);
    }

    @Test
    void getDownloadLinkRejectsMissingDeployedTime() {
        App app = deployedFieldsApp();
        app.setDeployedTime(null);
        when(appService.getById(APP_ID)).thenReturn(app);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> appController.getDownloadLink(APP_ID, request));

        assertDownloadBlocked(exception);
        verify(projectDownloadService, never()).createDownloadToken(APP_ID, USER_ID);
    }

    @Test
    void downloadAppCodeRejectsUndeployedAppBeforeTokenOrFileAccess() {
        App app = deployedFieldsApp();
        app.setDeployKey(null);
        when(appService.getById(APP_ID)).thenReturn(app);
        HttpServletResponse response = mock(HttpServletResponse.class);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> appController.downloadAppCode(APP_ID, request, response));

        assertDownloadBlocked(exception);
        verify(request, never()).getParameter("token");
        verify(projectDownloadService, never()).validateDownloadToken("token", APP_ID, USER_ID);
        verify(projectDownloadService, never()).downloadProjectAsZip(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.same(request),
                org.mockito.ArgumentMatchers.same(response));
    }

    private App deployedFieldsApp() {
        App app = new App();
        app.setId(APP_ID);
        app.setUserId(USER_ID);
        app.setDeployKey("html_" + APP_ID);
        app.setDeployedTime(LocalDateTime.now());
        app.setCodeGenType("html");
        return app;
    }

    private void assertDownloadBlocked(BusinessException exception) {
        assertEquals(ErrorCode.FORBIDDEN_ERROR.getCode(), exception.getCode());
        assertEquals(NOT_DEPLOYED_MESSAGE, exception.getMessage());
    }
}
