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

    // 每个应用生成的最大文件数量限制，防止死循环
    private static final int MAX_FILES_PER_APP = 50;
    
    // 记录每个应用已写入的文件数量
    private final Map<Long, AtomicInteger> appFileCountMap = new ConcurrentHashMap<>();

    /**
     * 重置指定应用的写入计数（在每次重新生成开始时调用）
     * @param appId 应用ID
     */
    public void resetFileCount(Long appId) {
        if (appId != null) {
            appFileCountMap.remove(appId);
        }
    }

    @Tool("写入文件到指定路径")
    public String writeFile(@P("文件的相对路径") String relativeFilePath,
                            @P("要写入文件的内容") String content,
                            @ToolMemoryId Long appId) {
        // 0. 检查写入次数限制
        if (appId != null) {
            AtomicInteger count = appFileCountMap.computeIfAbsent(appId, k -> new AtomicInteger(0));
            // 每次调用前打印日志，避免无限打印
            log.info("AppId: {} 正在写入文件: {} (当前第 {} 个文件)", appId, relativeFilePath, count.get() + 1);
            
            if (count.get() >= MAX_FILES_PER_APP) {
                String msg = String.format("系统限制：单个项目最多允许生成 %d 个文件，已停止写入。请停止调用工具。", MAX_FILES_PER_APP);
                log.warn("AppId: {} 达到文件写入限制", appId);
                return msg;
            }
            count.incrementAndGet();
        } else {
            // 如果 appId 为空，为了安全起见，打印警告
            log.warn("writeFile called with null appId. Limit check skipped but file will be written to null project dir (risky).");
        }

        // 1. 基础校验：必须是相对路径，禁止路径穿越
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            return "文件写入失败：文件路径不能为空";
        }

        Path relativePath = Paths.get(relativeFilePath);
        if (relativePath.isAbsolute()) {
            String msg = "文件写入失败：仅允许相对路径，禁止绝对路径 -> " + relativeFilePath;
            log.warn(msg);
            return msg;
        }
        // 简单防御：拒绝包含路径穿越标记
        if (relativeFilePath.contains("..")) {
            String msg = "文件写入失败：检测到非法路径（禁止包含 ..）-> " + relativeFilePath;
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
                String msg = "文件写入失败：目标路径超出允许的项目根目录 -> " + relativeFilePath;
                log.warn(msg);
                return msg;
            }

            // 4. 自动创建父目录
            Path parentDir = targetPath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            // 5. 写入文件（覆盖模式）
            Files.writeString(targetPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("AppId: {} 文件写入成功: {}", appId, relativeFilePath);
            return "文件写入成功：" + relativeFilePath;

        } catch (IOException e) {
            log.error("文件写入异常: appId={}, path={}, error={}", appId, relativeFilePath, e.getMessage());
            return "文件写入发生系统错误：" + e.getMessage();
        }
    }
}
