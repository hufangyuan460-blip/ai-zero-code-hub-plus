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

    @Tool("写入文件到指定路径")
    public String writeFile(@P("文件的相对路径") String relativeFilePath,
                            @P("要写入文件的内容") String content,
                            @ToolMemoryId Long appId) {
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
            String projectDirName = "vue_project_" + appId;
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

            // 5. 写入文件内容（覆盖同名文件）
            Files.write(targetPath, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件，相对路径：{}，物理路径：{}", relativeFilePath, targetPath);
            // 仅返回相对路径，避免泄露服务器绝对路径
            return "文件写入成功：" + relativeFilePath;

        } catch (IOException e) {
            String errorMessage = "文件写入失败：" + relativeFilePath + "，错误：" + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }
}
