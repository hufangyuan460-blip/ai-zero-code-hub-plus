package com.swu.aiZeroCodeHub.aiTool;

import com.swu.aiZeroCodeHub.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * 文件目录树工具，供推理模型递归获取项目的文件结构。
 */
@Slf4j
@Component
public class FileTreeTool implements AiTool {

    @Tool("获取项目的目录结构树")
    public String getProjectFileTree(@P("要查询的子目录相对路径，不传则查询根目录") String relativeDirPath,
                                     @ToolMemoryId Long appId) {
        String dirPath = (relativeDirPath == null || relativeDirPath.isBlank()) ? "" : relativeDirPath;
        
        if (dirPath.contains("..") || Paths.get(dirPath).isAbsolute()) {
             return "获取目录失败：非法路径 " + dirPath;
        }

        try {
            String projectDirName = "vue_project_" + appId;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName).toAbsolutePath().normalize();
            Path targetPath = projectRoot.resolve(dirPath).normalize();

            if (!targetPath.startsWith(projectRoot)) {
                return "获取目录失败：越权访问";
            }

            if (!Files.exists(targetPath)) {
                return "目录不存在: " + dirPath;
            }

            StringBuilder treeBuilder = new StringBuilder();
            treeBuilder.append("Directory structure of ").append(dirPath.isEmpty() ? "root" : dirPath).append(":\n");

            // 递归遍历文件树，使用 walk
            try (Stream<Path> paths = Files.walk(targetPath)) {
                paths.forEach(path -> {
                    // 计算相对路径
                    Path relPath = projectRoot.relativize(path);
                    int depth = relPath.getNameCount();
                    
                    // 忽略 dist, node_modules, .git 等
                    String pathStr = relPath.toString().replace(File.separator, "/");
                    if (pathStr.contains("node_modules") || pathStr.contains(".git") || pathStr.contains("dist")) {
                         return; 
                    }
                    if (path.equals(projectRoot)) return; // skip root itself in print if needed

                    String indent = "  ".repeat(Math.max(0, depth - 1));
                    String prefix = Files.isDirectory(path) ? "[DIR] " : "[FILE] ";
                    treeBuilder.append(indent).append(prefix).append(path.getFileName()).append("\n");
                });
            }
            return treeBuilder.toString();

        } catch (IOException e) {
            log.error("获取目录结构失败", e);
            return "获取目录结构失败：" + e.getMessage();
        }
    }
}
