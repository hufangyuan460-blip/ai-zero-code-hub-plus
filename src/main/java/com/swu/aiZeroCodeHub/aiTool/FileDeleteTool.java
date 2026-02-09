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

/**
 * 文件删除工具，供推理模型删除不再需要的文件。
 */
@Slf4j
@Component
public class FileDeleteTool implements AiTool {

    @Tool("删除指定文件")
    public String deleteFile(@P("文件的相对路径") String relativeFilePath,
                             @ToolMemoryId Long appId) {
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            return "删除失败：路径不能为空";
        }
        if (relativeFilePath.contains("..") || Paths.get(relativeFilePath).isAbsolute()) {
            return "删除失败：非法路径 " + relativeFilePath;
        }

        try {
            String projectDirName = "vue_project_" + appId;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName).toAbsolutePath().normalize();
            Path targetPath = projectRoot.resolve(relativeFilePath).normalize();

            if (!targetPath.startsWith(projectRoot)) {
                return "删除失败：越权操作";
            }

            if (!Files.exists(targetPath)) {
                return "删除失败：文件不存在 " + relativeFilePath;
            }

            Files.delete(targetPath);
            log.info("成功删除文件: {}", targetPath);
            return "成功删除文件：" + relativeFilePath;

        } catch (IOException e) {
            log.error("删除文件失败: {}", relativeFilePath, e);
            return "删除文件失败：" + e.getMessage();
        }
    }
}
