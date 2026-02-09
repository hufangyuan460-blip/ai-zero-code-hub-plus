package com.swu.aiZeroCodeHub.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.swu.aiZeroCodeHub.constant.AppConstant;
import com.swu.aiZeroCodeHub.common.utils.WebScreenshotUtils;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.manager.CosManager;
import com.swu.aiZeroCodeHub.service.ScreenshotService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {
    @Resource
    private CosManager cosManager;

    @Override
    public String generateAndUploadScreenshot(Long appId, String webUrl) {
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(StrUtil.isBlank(webUrl), ErrorCode.PARAM_ERROR, "网页URL不能为空");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "appId不能为空");

        log.info("开始生成网页截图，URL: {}", webUrl);

        // 1. 生成本地截图
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(StrUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR, "本地截图生成失败");

        try {
            File coverFile = saveCoverToLocalFile(appId, localScreenshotPath);
            String localUrl = buildCoverUrl(appId);
            // 2. 上传到对象存储（使用本地落盘文件，确保一致）
            String cosUrl = uploadScreenshotToCos(coverFile, appId);
            if (StrUtil.isBlank(cosUrl)) {
                return localUrl;
            }

            log.info("网页截图生成并上传成功: {} -> {}", webUrl, cosUrl);
            return cosUrl;
        } finally {
            // 3. 清理本地文件
            cleanupLocalFile(localScreenshotPath);
        }
    }

    /**
     * 上传截图到对象存储
     * @param localScreenshotPath 本地截图路径
     * @param appId 应用ID（用于统一命名）
     * @return 对象存储访问URL，失败返回null
     */
    private String uploadScreenshotToCos(File coverFile, Long appId) {
        if (coverFile == null) {
            return null;
        }

        if (!coverFile.exists()) {
            log.error("截图文件不存在: {}", coverFile.getAbsolutePath());
            return null;
        }

        String fileName = appId + ".jpg";
        String cosKey = buildCoverCosKey(fileName);
        return cosManager.uploadFile(cosKey, coverFile);
    }

    private String buildCoverCosKey(String fileName) {
        return String.format("covers/%s", fileName);
    }

    /**
     * 清理本地文件
     * @param localFilePath 本地文件路径
     */
    private void cleanupLocalFile(String localFilePath) {
        File localFile = new File(localFilePath);
        if (localFile.exists()) {
            File parentDir = localFile.getParentFile();
            FileUtil.del(parentDir);
            log.info("本地截图文件已清理: {}", localFilePath);
        }
    }

    private File saveCoverToLocalFile(Long appId, String localScreenshotPath) {
        if (StrUtil.isBlank(localScreenshotPath)) {
            return null;
        }
        File screenshotFile = new File(localScreenshotPath);
        if (!screenshotFile.exists()) {
            log.error("截图文件不存在: {}", localScreenshotPath);
            return null;
        }
        String coversDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "covers";
        File coversDir = new File(coversDirPath);
        FileUtil.mkdir(coversDir);
        String fileName = appId + ".jpg";
        File targetFile = new File(coversDir, fileName);
        FileUtil.copy(screenshotFile, targetFile, true);
        return targetFile;
    }

    private String buildCoverUrl(Long appId) {
        return String.format("/api/static/covers/%d.jpg", appId);
    }
}
