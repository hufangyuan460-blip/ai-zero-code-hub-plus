package com.swu.aiZeroCodeHub.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.exception.ThrowUtils;
import com.swu.aiZeroCodeHub.service.ProjectDownloadService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {
    private static final String DOWNLOAD_TOKEN_PREFIX = "app:download:token:";
    private static final Duration DOWNLOAD_TOKEN_TTL = Duration.ofMinutes(10);
    private static final String DOWNLOAD_TEMP_DIR = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "downloads";

    /**
     * 需要过滤的文件和目录名称
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".DS_Store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    /**
     * 需要过滤的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 检查路径是否允许包含在压缩包中
     * @param projectRoot 项目根目录
     * @param fullPath 完整路径
     * @return 是否允许
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        // 获取相对路径
        Path relativePath = projectRoot.relativize(fullPath);

        // 检查路径中的每一部分
        for (Path part : relativePath) {
            String partName = part.toString();

            // 检查是否在忽略名称列表中
            if (IGNORED_NAMES.contains(partName)) {
                return false;
            }

            // 检查文件扩展名
            if (IGNORED_EXTENSIONS.stream().anyMatch(partName::endsWith)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String createDownloadToken(Long appId, Long userId) {
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(appId == null || appId <= 0, ErrorCode.PARAM_ERROR, "应用ID无效");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(userId == null || userId <= 0, ErrorCode.PARAM_ERROR, "用户ID无效");

        String token = UUID.randomUUID().toString().replace("-", "");
        String key = DOWNLOAD_TOKEN_PREFIX + token;
        String value = appId + ":" + userId;
        stringRedisTemplate.opsForValue().set(key, value, DOWNLOAD_TOKEN_TTL);
        return token;
    }

    @Override
    public boolean validateDownloadToken(String token, Long appId, Long userId) {
        if (StrUtil.isBlank(token) || appId == null || userId == null) {
            return false;
        }
        String value = stringRedisTemplate.opsForValue().get(DOWNLOAD_TOKEN_PREFIX + token);
        if (StrUtil.isBlank(value)) {
            return false;
        }
        String[] parts = value.split(":");
        if (parts.length != 2) {
            return false;
        }
        return String.valueOf(appId).equals(parts[0]) && String.valueOf(userId).equals(parts[1]);
    }

    @Override
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletRequest request, HttpServletResponse response) {
        // 基础校验
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(StrUtil.isBlank(projectPath), ErrorCode.PARAM_ERROR, "项目路径不能为空");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(StrUtil.isBlank(downloadFileName), ErrorCode.PARAM_ERROR, "下载文件名不能为空");

        File projectDir = new File(projectPath);
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(!projectDir.exists(), ErrorCode.NOT_FOUND_ERROR, "项目目录不存在");
        ThrowUtils.throwExceptionByConditionAndErrorCodeAndMessage(!projectDir.isDirectory(), ErrorCode.PARAM_ERROR, "指定路径不是目录");

        File zipFile = buildZipFile(projectDir, downloadFileName);
        long fileLength = zipFile.length();

        String rangeHeader = request.getHeader("Range");
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType("application/zip");
        response.addHeader("Content-Disposition", String.format("attachment; filename=\"%s.zip\"", downloadFileName));

        long[] range = parseRange(rangeHeader, fileLength);
        if (range == null) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentLengthLong(fileLength);
            try (InputStream in = new FileInputStream(zipFile); OutputStream out = response.getOutputStream()) {
                copyRange(in, out, 0, fileLength - 1);
                log.info("项目打包下载完成: {}", downloadFileName);
            } catch (Exception e) {
                log.error("项目打包下载异常", e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目打包下载失败");
            }
            return;
        }

        long start = range[0];
        long end = range[1];
        if (start < 0 || end < start || start >= fileLength) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + fileLength);
            return;
        }

        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader("Content-Range", String.format("bytes %d-%d/%d", start, end, fileLength));
        response.setContentLengthLong(end - start + 1);

        try (RandomAccessFile raf = new RandomAccessFile(zipFile, "r"); OutputStream out = response.getOutputStream()) {
            raf.seek(start);
            byte[] buffer = new byte[8192];
            long remaining = end - start + 1;
            while (remaining > 0) {
                int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
            log.info("项目断点下载完成: {} ({}-{})", downloadFileName, start, end);
        } catch (Exception e) {
            log.error("项目断点下载异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目打包下载失败");
        }
    }

    private File buildZipFile(File projectDir, String downloadFileName) {
        FileUtil.mkdir(DOWNLOAD_TEMP_DIR);
        File zipFile = new File(DOWNLOAD_TEMP_DIR, downloadFileName + ".zip");

        if (!zipFile.exists() || zipFile.lastModified() < projectDir.lastModified()) {
            log.info("开始打包下载项目: {} -> {}", projectDir.getAbsolutePath(), zipFile.getAbsolutePath());
            FileFilter filter = file -> isPathAllowed(projectDir.toPath(), file.toPath());
            try {
                ZipUtil.zip(zipFile, StandardCharsets.UTF_8, false, filter, projectDir);
                log.info("项目打包完成: {}", zipFile.getName());
            } catch (Exception e) {
                log.error("项目打包异常", e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目打包下载失败");
            }
        }
        return zipFile;
    }

    private long[] parseRange(String rangeHeader, long fileLength) {
        if (StrUtil.isBlank(rangeHeader) || !rangeHeader.startsWith("bytes=")) {
            return null;
        }
        String rangeValue = rangeHeader.substring(6).split(",")[0].trim();
        long start;
        long end;
        try {
            if (rangeValue.startsWith("-")) {
                long suffix = Long.parseLong(rangeValue.substring(1));
                if (suffix <= 0) {
                    return new long[]{-1, -1};
                }
                start = Math.max(fileLength - suffix, 0);
                end = fileLength - 1;
            } else if (rangeValue.endsWith("-")) {
                start = Long.parseLong(rangeValue.substring(0, rangeValue.length() - 1));
                end = fileLength - 1;
            } else {
                String[] parts = rangeValue.split("-");
                if (parts.length != 2) {
                    return new long[]{-1, -1};
                }
                start = Long.parseLong(parts[0]);
                end = Long.parseLong(parts[1]);
            }
        } catch (Exception e) {
            return new long[]{-1, -1};
        }
        return new long[]{start, end};
    }

    private void copyRange(InputStream in, OutputStream out, long start, long end) throws Exception {
        if (start > 0) {
            long skipped = in.skip(start);
            while (skipped < start) {
                long delta = in.skip(start - skipped);
                if (delta <= 0) {
                    break;
                }
                skipped += delta;
            }
        }
        byte[] buffer = new byte[8192];
        long remaining = end - start + 1;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) {
                break;
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }
}




