package com.swu.aiZeroCodeHub.aiTool;

import com.swu.aiZeroCodeHub.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件读取工具，供推理模型读取指定文件的内容。
 */
@Slf4j
@Component
public class FileReadTool implements AiTool {

    @Tool("读取指定文件的内容")
    public String readFile(@P("文件的相对路径") String relativeFilePath,
                           @ToolMemoryId Long appId) {
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            return "文件读取失败：路径不能为空";
        }

        Path relativePath = Paths.get(relativeFilePath);
        if (relativePath.isAbsolute() || relativeFilePath.contains("..")) {
            return "文件读取失败：非法路径 " + relativeFilePath;
        }

        try {
            String projectDirName = "vue_project_" + appId;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName).toAbsolutePath().normalize();
            Path targetPath = projectRoot.resolve(relativePath).normalize();

            if (!targetPath.startsWith(projectRoot)) {
                return "文件读取失败：越权访问 " + relativeFilePath;
            }

            if (!Files.exists(targetPath)) {
                return "文件读取失败：文件不存在 " + relativeFilePath;
            }

            if (!Files.isRegularFile(targetPath)) {
                return "文件读取失败：不是一个文件 " + relativeFilePath;
            }

            String content = Files.readString(targetPath, StandardCharsets.UTF_8);
            return "文件内容(" + relativeFilePath + "):\n" + content;

        } catch (IOException e) {
            log.error("读取文件失败: {}", relativeFilePath, e);
            return "文件读取失败：" + e.getMessage();
        }
    }
}
