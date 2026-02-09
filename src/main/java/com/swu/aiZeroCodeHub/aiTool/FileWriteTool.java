package com.swu.aiZeroCodeHub.aiTool;

import com.swu.aiZeroCodeHub.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件写入工具，供推理模型在生成 Vue 工程项目时调用。
 *
 * <p>安全约束：</p>
 * <ul>
 *     <li>仅允许写入相对路径（基于应用级根目录），禁止绝对路径；</li>
 *     <li>禁止路径穿越（如包含 ".." 等）；</li>
 *     <li>返回值只暴露相对路径，不返回服务器真实物理路径。</li>
 * </ul>
 */
@Slf4j
@Component
public class FileWriteTool {

    // 记录每个应用已写入的文件数量
    private final Map<Long, AtomicInteger> appFileCountMap = new ConcurrentHashMap<>();
    private final Map<Long, Long> appLastWriteTimeMap = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> appGenerationCompletedMap = new ConcurrentHashMap<>();

    /**
     * 重置指定应用的写入计数（在每次重新生成开始时调用）
     * @param appId 应用ID
     */
    public void resetFileCount(Long appId) {
        if (appId != null) {
            appFileCountMap.remove(appId);
            appLastWriteTimeMap.remove(appId);
            appGenerationCompletedMap.remove(appId);
        }
    }

    public void markGenerationStarted(Long appId) {
        if (appId != null) {
            appFileCountMap.remove(appId);
            appLastWriteTimeMap.remove(appId);
            appGenerationCompletedMap.put(appId, false);
        }
    }

    public void markGenerationCompleted(Long appId) {
        if (appId != null) {
            appGenerationCompletedMap.put(appId, true);
        }
    }

    public void markGenerationFailed(Long appId) {
        if (appId != null) {
            appGenerationCompletedMap.put(appId, false);
        }
    }

    public boolean hasGenerationStatus(Long appId) {
        return appId != null && appGenerationCompletedMap.containsKey(appId);
    }

    public boolean isGenerationCompleted(Long appId) {
        return appId != null && Boolean.TRUE.equals(appGenerationCompletedMap.get(appId));
    }

    public boolean isWriteIdle(Long appId, long idleMs) {
        if (appId == null) {
            return true;
        }
        Long lastWrite = appLastWriteTimeMap.get(appId);
        if (lastWrite == null) {
            return true;
        }
        return System.currentTimeMillis() - lastWrite >= idleMs;
    }

    public int getFileCount(Long appId) {
        if (appId == null) {
            return 0;
        }
        AtomicInteger count = appFileCountMap.get(appId);
        return count == null ? 0 : count.get();
    }

    @Tool("writeFile")
    public String writeFile(@P("relativeFilePath") String relativeFilePath,
                            @P("content") String content,
                            @ToolMemoryId Long appId) {
        // 0. 记录写入次数
        if (appId != null) {
            AtomicInteger count = appFileCountMap.computeIfAbsent(appId, k -> new AtomicInteger(0));
            // 每次调用前打印日志，避免无限打印
            log.info("AppId: {} 正在写入文件: {} (当前第 {} 个文件)", appId, relativeFilePath, count.get() + 1);
            count.incrementAndGet();
            appLastWriteTimeMap.put(appId, System.currentTimeMillis());
        } else {
            // 如果 appId 为空，为了安全起见，打印警告
            log.warn("writeFile called with null appId. Limit check skipped but file will be written to null project dir (risky).");
        }

        // 1. 基础校验：必须是相对路径，禁止路径穿越
        if (relativeFilePath == null) {
            return "文件写入失败：文件路径不能为空";
        }

        // 允许常见前缀并进行规范化：去掉前导空白、./、.\\、/、\\
        String normalizedPath = relativeFilePath.trim();
        while (normalizedPath.startsWith("./") || normalizedPath.startsWith(".\\")
                || normalizedPath.startsWith("/") || normalizedPath.startsWith("\\")) {
            normalizedPath = normalizedPath.substring(1);
        }
        if (normalizedPath.isBlank()) {
            return "文件写入失败：文件路径不能为空";
        }

        Path relativePath = Paths.get(normalizedPath);
        if (relativePath.isAbsolute()) {
            String msg = "文件写入失败：仅允许相对路径，禁止绝对路径 -> " + normalizedPath;
            log.warn(msg);
            return msg;
        }
        // 简单防御：拒绝包含路径穿越标记
        if (normalizedPath.contains("..")) {
            String msg = "文件写入失败：检测到非法路径（禁止包含 ..）-> " + normalizedPath;
            log.warn(msg);
            return msg;
        }

        try {
            // 2. 基于 appId 构造项目根目录：{CODE_OUTPUT_ROOT_DIR}/vue_project_{appId}
            String projectDirName = "vue_project_" + (appId == null ? "unknown" : appId);
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName).toAbsolutePath().normalize();

            // 3. 组装最终写入路径，并再次校验不越界
            Path targetPath = projectRoot.resolve(relativePath).normalize();
            if (!targetPath.startsWith(projectRoot)) {
                String msg = "文件写入失败：目标路径超出允许的项目根目录 -> " + normalizedPath;
                log.warn(msg);
                return msg;
            }

            // 4. 自动创建父目录
            Path parentDir = targetPath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            // 5. 写入文件（覆盖模式）
            Files.writeString(targetPath, content == null ? "" : content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("AppId: {} 文件写入成功: {}", appId, normalizedPath);
            return "文件写入成功：" + normalizedPath;

        } catch (IOException e) {
            log.error("文件写入异常: appId={}, path={}, error={}", appId, normalizedPath, e.getMessage());
            return "文件写入发生系统错误：" + e.getMessage();
        }
    }
}
