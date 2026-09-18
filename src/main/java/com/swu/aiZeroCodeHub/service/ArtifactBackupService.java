package com.swu.aiZeroCodeHub.service;

import com.swu.aiZeroCodeHub.constant.AppConstant;
import com.swu.aiZeroCodeHub.generation.GenerationRunProperties;
import com.swu.aiZeroCodeHub.generation.GenerationRunState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Per-run backup for the minimum safe fallback. It only touches the exact
 * generated directory names for one app and never performs wildcard deletion.
 */
@Service
@Slf4j
public class ArtifactBackupService {

    private static final List<String> TYPES = List.of("html", "multi_file", "vue_project");

    @Resource
    private GenerationRunProperties runProperties;
    @Resource
    private GenerationRunStateService runStateService;
    private final Path testOutputRoot;
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "agent-artifact-retention");
        thread.setDaemon(true);
        return thread;
    });

    public ArtifactBackupService() {
        this.testOutputRoot = null;
    }

    ArtifactBackupService(Path testOutputRoot) {
        this.testOutputRoot = testOutputRoot.toAbsolutePath().normalize();
    }

    @PostConstruct
    void scheduleCleanup() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredBackups, 1, 1, TimeUnit.HOURS);
    }

    @PreDestroy
    void stopCleanup() {
        cleanupExecutor.shutdownNow();
    }

    public void prepare(Long appId, String runId) {
        if (!valid(appId, runId)) return;
        Path runRoot = runRoot(runId);
        try {
            Files.createDirectories(runRoot);
            List<String> existing = new ArrayList<>();
            for (String type : TYPES) {
                Path source = outputRoot().resolve(type + "_" + appId).normalize();
                if (!isInside(outputRoot(), source) || !Files.exists(source)) continue;
                existing.add(type);
                copyTree(source, runRoot.resolve(type));
            }
            Files.write(runRoot.resolve("manifest"), existing);
        } catch (Exception e) {
            throw new IllegalStateException("生成前备份失败", e);
        }
    }

    public void restore(Long appId, String runId) {
        if (!valid(appId, runId)) return;
        Path runRoot = runRoot(runId);
        try {
            List<String> existing = Files.isRegularFile(runRoot.resolve("manifest"))
                    ? Files.readAllLines(runRoot.resolve("manifest")) : List.of();
            for (String type : TYPES) {
                Path target = outputRoot().resolve(type + "_" + appId).normalize();
                if (!isInside(outputRoot(), target)) continue;
                deleteTree(target);
                if (existing.contains(type)) copyTree(runRoot.resolve(type), target);
            }
        } catch (Exception e) {
            log.warn("生成失败后恢复备份失败，appId={}, runId={}, reason={}", appId, runId, safeReason(e));
        }
    }

    public void commit(Long appId, String runId) {
        cleanupExpiredBackups();
    }

    /**
     * Removes only expired, UUID-named run directories under the exact backup root.
     * A stale or malformed directory is intentionally left untouched.
     */
    public void cleanupExpiredBackups() {
        Path root = outputRoot();
        Path runsRoot = root.resolve(".agent").resolve("runs").normalize();
        if (!isInside(root, runsRoot) || !Files.isDirectory(runsRoot)) {
            return;
        }
        java.time.Instant deadline = java.time.Instant.now()
                .minus(runProperties == null ? java.time.Duration.ofHours(24)
                        : runProperties.effectiveArtifactRetention());
        try (var children = Files.list(runsRoot)) {
            children.filter(Files::isDirectory).forEach(runDir -> {
                Path normalized = runDir.toAbsolutePath().normalize();
                String name = normalized.getFileName() == null ? "" : normalized.getFileName().toString();
                if (!normalized.getParent().equals(runsRoot) || !isUuid(name)) {
                    return;
                }
                if (isActive(name)) {
                    return;
                }
                try {
                    if (Files.getLastModifiedTime(normalized).toInstant().isBefore(deadline)) {
                        deleteTree(normalized);
                    }
                } catch (Exception e) {
                    log.warn("运行备份清理失败，runId={}, reason={}", name, safeReason(e));
                }
            });
        } catch (IOException e) {
            log.warn("运行备份目录扫描失败，reason={}", safeReason(e));
        }
    }

    private Path outputRoot() {
        return testOutputRoot == null
                ? Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR).toAbsolutePath().normalize()
                : testOutputRoot;
    }

    private Path runRoot(String runId) {
        return outputRoot().resolve(".agent").resolve("runs").resolve(runId).normalize();
    }

    private boolean valid(Long appId, String runId) {
        return appId != null && appId > 0 && runId != null && runId.matches("[0-9a-fA-F-]{36}");
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isActive(String runId) {
        if (runStateService == null) {
            return false;
        }
        try {
            return runStateService.find(runId).map(GenerationRunState::isActive).orElse(false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String safeReason(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return error.getClass().getSimpleName();
        String sanitized = message.replaceAll("[A-Za-z]:\\\\[^\\n\\r]*|/(?:[^\\n\\r ]+/)+[^\\n\\r ]*", "[路径]")
                .replaceAll("(?i)(token|password|secret|api[_-]?key)\\s*[:=]\\s*[^,;\\s]+", "$1=***")
                ;
        return sanitized.substring(0, Math.min(200, sanitized.length()));
    }

    private boolean isInside(Path root, Path path) {
        return path.startsWith(root) && !path.equals(root);
    }

    private void copyTree(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative).normalize();
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else if (Files.isRegularFile(path)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            for (Path current : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }
}
