package com.swu.aiZeroCodeHub.core.build;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Explicit development-only executor. It never accepts a shell command string. */
@Component
@Profile("development")
@Slf4j
public class LocalDevelopmentBuildExecutor implements ProjectBuildExecutor {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public BuildExecutionResult build(BuildRequest request) {
        if (request == null || request.workingDirectory() == null
                || !Files.isDirectory(request.workingDirectory())) {
            return new BuildExecutionResult(false, "构建目录无效", -1, false, "INVALID_DIRECTORY");
        }
        try {
            String npm = System.getProperty("os.name", "").toLowerCase().contains("windows")
                    ? "npm.cmd" : "npm";
            BuildExecutionResult install = run(List.of(npm, "install"), request);
            if (!install.success()) return install;
            return run(List.of(npm, "run", "build"), request);
        } catch (Exception e) {
            log.warn("开发构建执行失败，类型={}", e.getClass().getSimpleName());
            return new BuildExecutionResult(false, "开发构建执行失败", -1, false, "EXECUTION_ERROR");
        }
    }

    private BuildExecutionResult run(List<String> command, BuildRequest request) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command))
                .directory(request.workingDirectory().toFile())
                .redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        Thread reader = Thread.startVirtualThread(() -> readBounded(process.getInputStream(), output,
                Math.max(1_000, request.maxOutputChars())));
        Duration timeout = request.timeout() == null ? Duration.ofMinutes(5) : request.timeout();
        boolean finished = process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            reader.interrupt();
            return new BuildExecutionResult(false, scrub(output.toString()), -1, true, "TIMEOUT");
        }
        reader.join(1_000);
        int exitCode = process.exitValue();
        String summary = scrub(output.toString());
        return new BuildExecutionResult(exitCode == 0, summary, exitCode, false,
                exitCode == 0 ? null : "NON_ZERO_EXIT");
    }

    private void readBounded(InputStream stream, StringBuilder output, int maxChars) {
        try (stream) {
            byte[] buffer = new byte[2048];
            int read;
            while ((read = stream.read(buffer)) >= 0 && output.length() < maxChars) {
                output.append(new String(buffer, 0, Math.min(read, maxChars - output.length()), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // The bounded summary is best-effort and must not expose process details.
        }
    }

    private String scrub(String value) {
        return value.replaceAll("(?i)(token|password|secret|api[_-]?key)\\s*[:=]\\s*[^\\s]+", "$1=***")
                .replaceAll("[A-Za-z]:\\\\[^\\r\\n]*|/(?:[^\\r\\n ]+/)+[^\\r\\n ]*", "[路径]")
                .replaceAll("\\r?\\n", " ")
                .trim();
    }
}
