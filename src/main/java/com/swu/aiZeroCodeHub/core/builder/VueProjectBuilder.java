package com.swu.aiZeroCodeHub.core.builder;

import com.swu.aiZeroCodeHub.core.build.BuildExecutionResult;
import com.swu.aiZeroCodeHub.core.build.BuildRequest;
import com.swu.aiZeroCodeHub.core.build.ProjectBuildExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Vue 项目构建门面。实际进程执行由环境绑定的 ProjectBuildExecutor 决定：
 * 生产默认是安全拒绝，development profile 才允许本地 npm 执行。
 */
@Slf4j
@Component
public class VueProjectBuilder {

    private final ProjectBuildExecutor buildExecutor;

    public VueProjectBuilder(ProjectBuildExecutor buildExecutor) {
        this.buildExecutor = buildExecutor;
    }

    public boolean isBuildCapabilityAvailable() {
        return buildExecutor != null && buildExecutor.isAvailable();
    }

    public boolean buildProject(String projectPath) {
        BuildExecutionResult result = buildProjectResult(projectPath);
        return result.success() && Files.isDirectory(Path.of(projectPath).toAbsolutePath().normalize().resolve("dist"));
    }

    public BuildExecutionResult buildProjectResult(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return new BuildExecutionResult(false, "Vue 项目路径无效", -1, false, "INVALID_DIRECTORY");
        }
        Path projectDir = Path.of(projectPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(projectDir) || !Files.isRegularFile(projectDir.resolve("package.json"))) {
            log.warn("Vue 项目目录或 package.json 不存在");
            return new BuildExecutionResult(false, "Vue 项目目录或 package.json 不存在", -1, false,
                    "INVALID_DIRECTORY");
        }
        BuildExecutionResult result = buildExecutor.build(new BuildRequest(
                projectDir, Duration.ofMinutes(5), 6_000, 1_024, 100));
        if (!result.success()) {
            log.warn("Vue 项目构建失败，reason={}", result.failureReason());
            removeIncompleteDist(projectDir);
        }
        return result;
    }

    private void removeIncompleteDist(Path projectDir) {
        Path dist = projectDir.resolve("dist").normalize();
        if (!dist.startsWith(projectDir) || !Files.exists(dist)) return;
        try (var paths = Files.walk(dist)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (Exception e) {
            log.warn("清理 Vue 构建产物失败，类型={}", e.getClass().getSimpleName());
        }
    }

    public void buildProjectAsync(String projectPath) {
        buildProject(projectPath);
    }
}
