package com.swu.aiZeroCodeHub.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swu.aiZeroCodeHub.core.build.BuildExecutionResult;
import com.swu.aiZeroCodeHub.core.build.BuildRequest;
import com.swu.aiZeroCodeHub.core.build.ProjectBuildExecutor;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Small, deterministic validator used before any optional LLM quality check.
 * It reports paths and bounded summaries only, never source contents.
 */
@Service
@Slf4j
public class DeterministicValidationService implements ValidationService {

    private final ObjectMapper objectMapper;
    private final ProjectBuildExecutor buildExecutor;

    public DeterministicValidationService(ObjectMapper objectMapper, ProjectBuildExecutor buildExecutor) {
        this.objectMapper = objectMapper;
        this.buildExecutor = buildExecutor;
    }

    @Override
    public ValidationReport validate(Path artifactDirectory, CodeGenTypeEnum generationType) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<String> affectedFiles = listFiles(artifactDirectory);
        if (artifactDirectory == null || !Files.isDirectory(artifactDirectory)) {
            issues.add(new ValidationIssue("ARTIFACT_MISSING", "生成目录不存在", null, false));
            return report(false, issues, affectedFiles, null, null);
        }
        switch (generationType) {
            case HTML -> validateHtml(artifactDirectory, issues);
            case MULTI_FILE -> validateMultiFile(artifactDirectory, issues);
            case VUE_PROJECT -> validateVueStructure(artifactDirectory, issues);
            default -> issues.add(new ValidationIssue("TYPE_UNSUPPORTED", "不支持的项目类型", null, false));
        }
        String artifactHash = hashFiles(artifactDirectory);
        return report(issues.isEmpty(), issues, affectedFiles, artifactHash, null);
    }

    @Override
    public ValidationReport validateBuiltArtifact(Path artifactDirectory, CodeGenTypeEnum generationType,
                                                   BuildExecutionResult buildResult) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (artifactDirectory == null || !Files.isDirectory(artifactDirectory)) {
            issues.add(new ValidationIssue("ARTIFACT_MISSING", "生成目录不存在", null, false));
        } else if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            validateVueStructure(artifactDirectory, issues);
            if (buildResult == null || !buildResult.success()) {
                String reason = buildResult == null || buildResult.failureReason() == null
                        ? "Vue 项目构建失败" : buildResult.failureReason();
                issues.add(new ValidationIssue("BUILD_FAILED", reason, "package.json",
                        "NON_ZERO_EXIT".equals(buildResult == null ? null : buildResult.failureReason())));
            } else if (!Files.isDirectory(artifactDirectory.resolve("dist"))) {
                issues.add(new ValidationIssue("DIST_MISSING", "构建完成但缺少 dist 目录", "dist", false));
            }
        }
        String hash = hashFiles(artifactDirectory);
        if (buildResult != null && !buildResult.success()) {
            // 构建失败也需要与前置结构验证区分开，才能允许第一次定向修复；
            // 同一源码、同一失败结果再次出现时仍由指纹/产物哈希去重拦截。
            hash = sha256(hash + "|build:" + buildResult.failureReason() + ":" + buildResult.exitCode());
        }
        return report(issues.isEmpty(), issues, listFiles(artifactDirectory), hash,
                buildResult == null ? null : buildResult.outputSummary());
    }

    private void validateHtml(Path dir, List<ValidationIssue> issues) {
        Path index = dir.resolve("index.html");
        if (!Files.isRegularFile(index)) {
            issues.add(new ValidationIssue("INDEX_MISSING", "缺少 index.html", "index.html", true));
            return;
        }
        try {
            String content = Files.readString(index, StandardCharsets.UTF_8);
            if (!content.toLowerCase().contains("<html") || !content.toLowerCase().contains("</html>")) {
                issues.add(new ValidationIssue("HTML_INVALID", "index.html 缺少基本 HTML 结构", "index.html", true));
            }
        } catch (Exception e) {
            issues.add(new ValidationIssue("FILE_UNREADABLE", "无法读取 index.html", "index.html", true));
        }
    }

    private void validateMultiFile(Path dir, List<ValidationIssue> issues) {
        validateHtml(dir, issues);
        requireFile(dir, "style.css", issues);
        requireFile(dir, "script.js", issues);
        parseJsonFiles(dir, issues);
    }

    private void validateVueStructure(Path dir, List<ValidationIssue> issues) {
        Path packageJson = dir.resolve("package.json");
        if (!Files.isRegularFile(packageJson)) {
            issues.add(new ValidationIssue("PACKAGE_MISSING", "缺少 package.json", "package.json", true));
            return;
        }
        try {
            var tree = objectMapper.readTree(packageJson.toFile());
            if (tree.path("scripts").path("build").isMissingNode()) {
                issues.add(new ValidationIssue("BUILD_SCRIPT_MISSING", "package.json 缺少 build 脚本", "package.json", true));
                return;
            }
        } catch (Exception e) {
            issues.add(new ValidationIssue("PACKAGE_INVALID", "package.json 不是有效 JSON", "package.json", true));
            return;
        }
    }

    private void parseJsonFiles(Path dir, List<ValidationIssue> issues) {
        for (String relative : listFiles(dir)) {
            if (!relative.endsWith(".json") || relative.startsWith("node_modules/") || relative.startsWith("dist/")) continue;
            try {
                objectMapper.readTree(dir.resolve(relative).toFile());
            } catch (Exception e) {
                issues.add(new ValidationIssue("JSON_INVALID", "JSON 文件格式无效", relative, true));
            }
        }
    }

    private void requireFile(Path dir, String file, List<ValidationIssue> issues) {
        if (!Files.isRegularFile(dir.resolve(file))) {
            issues.add(new ValidationIssue("FILE_MISSING", "缺少 " + file, file, true));
        }
    }

    private ValidationReport report(boolean passed, List<ValidationIssue> issues, List<String> files,
                                    String artifactHash, String commandOutput) {
        String summary = passed ? "基础确定性验证通过（验证能力有限）"
                : issues.stream().limit(3).map(ValidationIssue::message).reduce((a, b) -> a + "；" + b)
                .orElse("确定性验证失败");
        boolean repairable = !issues.isEmpty() && issues.stream().allMatch(ValidationIssue::repairable);
        String fingerprint = sha256(String.join("|", issues.stream()
                .map(issue -> issue.code() + ":" + issue.message() + ":" + issue.filePath())
                .sorted().toList()) + "|" + (artifactHash == null ? "" : artifactHash));
        return new ValidationReport(passed, issues, summary, fingerprint, repairable, files,
                artifactHash, bounded(commandOutput));
    }

    private List<String> listFiles(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return List.of();
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> dir.relativize(path).toString().replace('\\', '/'))
                    .filter(path -> !path.startsWith("node_modules/") && !path.startsWith("dist/")
                            && !path.startsWith("target/") && !path.startsWith(".git/"))
                    .sorted(Comparator.naturalOrder()).limit(500).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String hashFiles(Path dir) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String file : listFiles(dir)) {
                digest.update(file.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                try (InputStream input = new DigestInputStream(Files.newInputStream(dir.resolve(file)), digest)) {
                    input.transferTo(java.io.OutputStream.nullOutputStream());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return "hash-unavailable";
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String bounded(String value) {
        if (value == null) return null;
        return value.length() <= 6_000 ? value : value.substring(0, 6_000);
    }
}
