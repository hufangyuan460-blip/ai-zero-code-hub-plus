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
import java.nio.file.StandardOpenOption;

/**
 * 文件修改工具，支持精确的查找替换（search & replace）。
 */
@Slf4j
@Component
public class FileModifyTool implements AiTool {

    @Tool("修改文件内容（查找并替换）")
    public String modifyFile(@P("文件的相对路径") String relativeFilePath,
                             @P("要查找的旧代码片段（必须完全匹配）") String oldContent,
                             @P("要替换的新代码片段") String newContent,
                             @ToolMemoryId Long appId) {
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            return "修改失败：路径不能为空";
        }
        if (oldContent == null || oldContent.isEmpty()) {
            return "修改失败：必须提供旧代码片段以进行定位";
        }

        try {
            String projectDirName = "vue_project_" + appId;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName).toAbsolutePath().normalize();
            Path targetPath = projectRoot.resolve(relativeFilePath).normalize();

            if (!targetPath.startsWith(projectRoot)) {
                return "修改失败：越权操作";
            }

            if (!Files.exists(targetPath)) {
                return "修改失败：文件不存在 " + relativeFilePath;
            }

            String currentFileContent = Files.readString(targetPath, StandardCharsets.UTF_8);

            // 执行查找替换
            // 简单的字符串替换，要求精确匹配（包括空格和换行）
            if (!currentFileContent.contains(oldContent)) {
                return "修改失败：在文件中未找到指定的旧代码片段，请确认缩进和换行是否完全一致。";
            }

            // 只替换第一个匹配项，或者全部替换？通常修改特定位置，replaceFirst 更稳妥，或者 replace（全部）。
            // 这里使用 replace，假设旧片段足够独特。如果需要更精细控制，可以使用 replaceFirst。
            String updatedContent = currentFileContent.replace(oldContent, newContent);

            Files.writeString(targetPath, updatedContent, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功修改文件: {}", targetPath);
            return "文件修改成功：" + relativeFilePath;

        } catch (IOException e) {
            log.error("修改文件失败: {}", relativeFilePath, e);
            return "修改文件失败：" + e.getMessage();
        }
    }
}
