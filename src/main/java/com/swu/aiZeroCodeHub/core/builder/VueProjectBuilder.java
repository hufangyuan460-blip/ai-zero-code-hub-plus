package com.swu.aiZeroCodeHub.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Vue 工程项目构建器：负责在生成完项目文件后，异步执行 npm 安装与构建。
 *
 * <p>关键点：</p>
 * <ul>
 *     <li>使用 Hutool 的 {@link RuntimeUtil#exec} 执行 Shell 命令；</li>
 *     <li>自动检测操作系统，在 Windows 下追加 <code>.cmd</code>；</li>
 *     <li>使用 Java 21 虚拟线程异步执行，避免阻塞主线程；</li>
 *     <li>对命令执行设置超时，异常 / 失败均记录日志便于排查。</li>
 * </ul>
 */
@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 在指定目录下执行命令。
     *
     * @param workingDir     工作目录
     * @param command        完整命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            if (workingDir == null || !workingDir.exists() || !workingDir.isDirectory()) {
                log.error("工作目录无效，无法执行命令，dir={}", workingDir);
                return false;
            }
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);

            // 使用 Hutool RuntimeUtil 执行命令
            Process process = RuntimeUtil.exec(null, workingDir, command.split("\\s+"));

            // 等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{} 秒），强制终止进程，command={}", timeoutSeconds, command);
                process.destroyForcibly();
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            } else {
                log.error("命令执行失败，退出码: {}，command={}", exitCode, command);
                return false;
            }
        } catch (Exception e) {
            log.error("执行命令失败: {}，错误信息: {}", command, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 执行 npm install。
     */
    private boolean executeNpmInstall(File projectDir) {
        log.info("开始执行 npm install，目录：{}", projectDir);
        String command = String.format("%s install", buildCommand("npm"));
        // 5 分钟超时
        return executeCommand(projectDir, command, 300);
    }

    /**
     * 执行 npm run build。
     */
    private boolean executeNpmBuild(File projectDir, boolean useRelativeBase) {
        log.info("开始执行 npm run build，目录：{}", projectDir);
        String command = useRelativeBase
                ? String.format("%s run build -- --base=./", buildCommand("npm"))
                : String.format("%s run build", buildCommand("npm"));
        // 3 分钟超时
        return executeCommand(projectDir, command, 180);
    }

    /**
     * 构建兼容 Windows / *nix 的命令前缀。
     */
    private String buildCommand(String baseCommand) {
        String command = baseCommand;
        if (System.getProperty("os.name").toUpperCase().contains("WINDOWS")) {
            command += ".cmd";
        }
        return command;
    }


    /**
     * 构建Vue项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return false;
        }

        // 检查package.json是否存在
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json文件不存在: {}", packageJson.getAbsolutePath());
            return false;
        }

        log.info("开始构建Vue项目: {}", projectPath);

        // 执行npm install
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install执行失败");
            return false;
        }

        boolean useRelativeBase = isViteProject(packageJson);
        boolean buildSuccess;
        if (useRelativeBase) {
            buildSuccess = executeNpmBuild(projectDir, true);
            if (!buildSuccess) {
                log.error("npm run build --base=./ 执行失败，尝试默认构建");
                buildSuccess = executeNpmBuild(projectDir, false);
            }
        } else {
            buildSuccess = executeNpmBuild(projectDir, false);
        }
        if (!buildSuccess) {
            log.error("npm run build执行失败");
            return false;
        }

        // 验证dist目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            log.error("构建完成但dist目录未生成: {}", distDir.getAbsolutePath());
            return false;
        }

        log.info("Vue项目构建成功，dist目录: {}", distDir.getAbsolutePath());
        return true;
    }

    private boolean isViteProject(File packageJson) {
        try {
            String content = Files.readString(packageJson.toPath(), StandardCharsets.UTF_8);
            return content.contains("\"vite\"")
                    || content.contains("@vitejs/plugin-vue")
                    || content.contains("vite build");
        } catch (Exception e) {
            log.error("读取package.json失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 启动异步构建任务（虚拟线程）。
     *
     * @param projectPath Vue 工程项目根目录（包含 package.json）
     */
    public void buildProjectAsync(String projectPath) {
        buildProject(projectPath);
    }
}
